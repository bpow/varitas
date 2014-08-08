package org.drpowell.vcffilters;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.drpowell.util.FileUtils;
import org.drpowell.util.Grouper;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMemoryCollection;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;

public class CompoundMutationFilter implements VCFIterator {
    
        private static LinkedHashMap<String, String> makeHashMap(String... strings) {
            if (strings.length % 2 != 0) {
                throw new IllegalArgumentException("makeHashMap called with odd number of arguments");
            }
            LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
            for (int i = 0; i < strings.length; i += 2) {
                map.put(strings[i], strings[i+1]);
            }
            return map;
        }
	
	// This needs some special handling because the exons of gene can rarely
	// interleave with each other -- so streamed processing would be complicated.
	// I'll just read all of the variants into memory instead...
	
	private VCFMemoryCollection collectedVariants;
	private Iterator<VCFVariant> filteredVariants;
	private final int [][] allTrioIndices;
	private int variantIndex = -1;
	private final VCFHeaders headers;
	private static final VCFMeta [] ADDITIONAL_HEADERS = {
		new VCFMeta("##INFO=<ID=COMPOUND,Number=.,Type=String,Description=\"Within this gene there is at least one variant not inherited from each parent of the listed individual.\">")
		, new VCFMeta("##INFO=<ID=RecessiveIndex,Number=1,Type=Integer,Description=\"Index of the variant within this file (used to refer between variants).\">")
		, new VCFMeta("##INFO=<ID=RecessivePartners,Number=.,Type=String,Description=\"List of indices participating in compound recessive grouping, formatted like: SAMPLE:id0|id2|id2.\">")
	};

	// FIXME - handle multiple trios somehow...
	public CompoundMutationFilter(VCFIterator delegate, List<int []> trioIndices) {
                allTrioIndices = new int[trioIndices.size()][3];
                int i = 0;
                for (int [] ti: trioIndices) {
                    allTrioIndices[i] = ti;
                    i++;
                }
		this.headers = new VCFHeaders(delegate.getHeaders());
		headers.addAll(Arrays.asList(ADDITIONAL_HEADERS));
		collectedVariants = new VCFMemoryCollection(delegate);
		HashMap<String, PhaseGroup[]> phaseGroups = buildPhaseGroups(collectedVariants);
		assignCompoundGroups(phaseGroups);
		filteredVariants = collectedVariants.iterator();
	}

	private PhaseGroup[] getDefaultPhaseGroup(HashMap<String, PhaseGroup[]> map, String key) {
		PhaseGroup[] pg = map.get(key);
		if (pg == null) {
			pg = new PhaseGroup[allTrioIndices.length];
                        for (int i = 0; i < pg.length; i++) {
                            pg[i] = new PhaseGroup();
                        }
			map.put(key, pg);
		}
		return pg;
	}
	
	private HashMap<String, PhaseGroup[]> buildPhaseGroups(Iterable<VCFVariant> variants) {
		HashMap<String, PhaseGroup[]> phaseGroups = new HashMap<String, PhaseGroup[]>();

		for (VCFVariant v : variants) {
                    for (int trio = 0; trio < allTrioIndices.length; trio++) {
                        int [] trioIndices = allTrioIndices[trio];
			// FIXME - use predetermined phase information if available

			// weird negative thinking-- these lists keep track of sites that have a variant that _didn't_ come from either dad or mom
			variantIndex++;
			v.putInfo("Index", Integer.toString(variantIndex));
			String [] calls = v.getCalls();
			int [] childCall = splitAlleles(calls[trioIndices[0]]);
			if (childCall[0] <= 0 && childCall[1] <= 0) {
				continue;
				// proband unknown or homozygous reference
			}
			PhaseGroup pg = getDefaultPhaseGroup(phaseGroups, v.getInfoValue("Gene_name"))[trio];
			int [] fatherCall = splitAlleles(calls[trioIndices[1]]);
			int [] motherCall = splitAlleles(calls[trioIndices[2]]);
			if (childCall[0] > 0 && childCall[1] > 0 &&
					(fatherCall[0] <= 0 || fatherCall[1] <= 0) &&
					(motherCall[0] <= 0 || motherCall[1] <= 0)) {
				pg.nonPaternal.add(v);
				pg.nonMaternal.add(v);
				// FIXME -these may not be all that interesting if one of the parents was already homalt
				// FIXME -this leads to duplicate values in the MendRecHet fields when these vars are paired with denovo
			} else {
				for (int allele: childCall) {
					if (allele > 0) { // only care about transmission of alt alleles
						boolean notInFather = indexOf(allele, fatherCall) < 0;
						boolean notInMother = indexOf(allele, motherCall) < 0;
						if (notInFather && notInMother) {
							pg.deNovo.add(v);
						} else if (notInFather) {
							pg.nonPaternal.add(v);
						} else if (notInMother) {
							pg.nonMaternal.add(v);
						}
					}
				}
			}
                    }
		}
		return phaseGroups;
	}
	
	private void assignCompoundGroups(Map<String, PhaseGroup[]> phaseGroups) {
		for (PhaseGroup[] pgs : phaseGroups.values()) {
			for (int trio = 0; trio < allTrioIndices.length; trio++) {
				String childName = headers.getSamples().get(allTrioIndices[trio][0]);
				PhaseGroup pg = pgs[trio];
				ArrayList<VCFVariant> nonMaternal = pg.nonMaternal;
				ArrayList<VCFVariant> nonPaternal = pg.nonPaternal;
				ArrayList<VCFVariant> deNovo = pg.deNovo;

				if ((!nonPaternal.isEmpty() && !pg.nonMaternal.isEmpty())
						|| deNovo.size() > 1
						|| pg.deNovo.size() * (nonMaternal.size() + nonPaternal.size()) > 0) {
					for (VCFVariant v : deNovo) {
						ArrayList<String> indices = new ArrayList<String>(); // TODO - initial size
						for (VCFVariant paired_variant : nonPaternal) {
							indices.add(paired_variant.getInfoValue("Index"));
						}
						for (VCFVariant paired_variant : nonMaternal) {
							indices.add(paired_variant.getInfoValue("Index"));
						}
						for (VCFVariant paired_variant : deNovo) {
							if (paired_variant != v)
								indices.add(paired_variant
										.getInfoValue("Index"));
						}
						if (!indices.isEmpty()) {
							v.addInfo("COMPOUND", childName);
							v.putInfo("RecessivePartners",
									String.format("%s:%s", childName, join("|", indices.toArray(new String[indices.size()]))));
						}
					}
					for (VCFVariant v : nonPaternal) {
						ArrayList<String> indices = new ArrayList<String>(); // TODO - initial size
						for (VCFVariant paired_variant : nonMaternal) {
							indices.add(paired_variant.getInfoValue("Index"));
						}
						for (VCFVariant paired_variant : deNovo) {
							indices.add(paired_variant.getInfoValue("Index"));
						}
						if (!indices.isEmpty()) {
							v.addInfo("COMPOUND", childName);
							v.putInfo("RecessivePartners",
									String.format("%s:%s", childName, join("|", indices.toArray(new String[indices.size()]))));
						}
					}
					for (VCFVariant v : nonMaternal) {
						ArrayList<String> indices = new ArrayList<String>(); // TODO
																				// -initial
																				// size
						for (VCFVariant paired_variant : nonPaternal) {
							indices.add(paired_variant.getInfoValue("Index"));
						}
						for (VCFVariant paired_variant : deNovo) {
							indices.add(paired_variant.getInfoValue("Index"));
						}
						if (!indices.isEmpty()) {
							v.addInfo("COMPOUND", childName);
							v.putInfo("RecessivePartners",
									String.format("%s:%s", childName, join("|", indices.toArray(new String[indices.size()]))));
						}
					}
				}
			}
		}
	}
	
	@Override
	public VCFHeaders getHeaders() {
		return headers;
	}

	private static final int [] splitAlleles(String call) {
		// FIXME - assumes GT FORMAT type is present (per VCF spec, if present must be first)
		if (call.startsWith(".")) {
			return new int [] {-1, -1};
		}
		int gtEnd = call.indexOf(':');
		if (gtEnd >= 0) {
			call = call.substring(0, gtEnd);
		}
		int delim = call.indexOf('/');
		if (delim < 0) delim = call.indexOf('|');
		if (delim < 0) return new int[] {-1, -1};
		return new int [] { Integer.parseInt(call.substring(0, delim)), Integer.parseInt(call.substring(delim+1)) };
	}
	
	private int indexOf(int needle, int [] haystack) {
		for (int i = 0; i < haystack.length; i++) {
			if (needle == haystack[i]) return i;
		}
		return -1;
	}
	
	
	@Override
	public boolean hasNext() {
		return filteredVariants.hasNext();
	}

	@Override
	public VCFVariant next() {
		return filteredVariants.next();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
	public static class VCFGeneGrouper extends Grouper<String, VCFVariant> {
		@Override
		public String keyForValue(VCFVariant v) {
			String gene = v.getInfoValue("Gene_name");
			if (gene == null || gene.isEmpty()) gene = null;
			return gene;
		}
	}
	
	public static void main(String argv[]) throws IOException {
		BufferedReader br = FileUtils.filenameToBufferedReader(argv[0]);
		VCFParser p = new VCFParser(br);
		VCFHeaders headers = p.getHeaders();
		headers.addAll(Arrays.asList(ADDITIONAL_HEADERS));
		System.out.print(headers);
		System.out.println(headers.getColumnHeaderLine());
		
		int yes = 0, no = 0;
		for (CompoundMutationFilter cmf = new CompoundMutationFilter(p, VCFUtils.getTrioIndices(p.getHeaders())); cmf.hasNext();) {
			VCFVariant v = cmf.next();
			if (v.hasInfo("COMPOUND")) {
				yes++;
			} else {
				no++;
			}
			System.out.println(v);
		}
		br.close();
		System.err.println(String.format("%d biallelic mutations,  %d otherwise", yes, no));
	}
	
	private class PhaseGroup {
		ArrayList<VCFVariant> nonPaternal = new ArrayList<VCFVariant>();
		ArrayList<VCFVariant> nonMaternal = new ArrayList<VCFVariant>();
		ArrayList<VCFVariant> deNovo = new ArrayList<VCFVariant>();
	}

	private static String join(String sep, String... strings) {
		// again?!?
		if (strings.length == 0) return "";
		if (strings.length == 1) return strings[0];
		StringBuilder sb = new StringBuilder();
		for (String s: strings) {
			sb.append(",").append(s);
		}
		return sb.substring(1);
	}

}
