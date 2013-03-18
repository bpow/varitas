package org.drpowell.vcffilters;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.drpowell.util.Grouper;
import org.drpowell.util.GunzipIfGZipped;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;

public class CompoundMutationFilter implements VCFIterator {
	
	private final Grouper<String, VCFVariant> grouper;
	private Iterator<VCFVariant> filteredVariants;
	private final int [] trioIndices;
	private int variantIndex = -1;
	private final VCFHeaders headers;
	private static final VCFMeta [] ADDITIONAL_HEADERS = {
		new VCFMeta("##INFO=<ID=COMPOUND,Number=0,Type=Flag,Description=\"Within this gene there is at least one variant not inherited from each parent.\">"),
		new VCFMeta("##INFO=<ID=Index,Number=1,Type=Integer,Description=\"Index of the variant within this file (used to refer between variants).\">"),
		new VCFMeta("##INFO=<ID=MendHetRec,Number=.,Type=Integer,Description=\"Comma-separated list of indices participating in compound recessive grouping.\">")
	};

	// FIXME - handle multiple trios somehow...
	public CompoundMutationFilter(VCFIterator delegate, int [] trioIndices) {
		this.trioIndices = trioIndices;
		this.headers = new VCFHeaders(delegate.getHeaders());
		headers.addAll(Arrays.asList(ADDITIONAL_HEADERS));
		grouper = new VCFGeneGrouper().setDelegate(delegate);
		advanceGroup();
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
	
	private void advanceGroup() {
		// time to get the next group of variants...
		Collection<VCFVariant> groupedVariants = grouper.next();
		
		// FIXME - use predetermined phase information if available
		
		// weird negative thinking-- these lists keep track of sites that have a variant that _didn't_ come from either dad or mom
		ArrayList<VCFVariant> nonPaternal = new ArrayList<VCFVariant>(groupedVariants.size());
		ArrayList<VCFVariant> nonMaternal = new ArrayList<VCFVariant>(groupedVariants.size());
		ArrayList<VCFVariant> deNovo = new ArrayList<VCFVariant>(groupedVariants.size());
		for (VCFVariant v : groupedVariants) {
			variantIndex++;
			v.putInfo("Index", Integer.toString(variantIndex));
			String [] calls = v.getCalls();
			int [] childCall = splitAlleles(calls[trioIndices[0]]);
			if (childCall[0] <= 0 && childCall[1] <= 0) {
				continue;
				// proband unknown or homozygous reference
			}
			int [] fatherCall = splitAlleles(calls[trioIndices[1]]);
			int [] motherCall = splitAlleles(calls[trioIndices[2]]);
			if (childCall[0] > 0 && childCall[1] > 0 &&
					(fatherCall[0] <= 0 || fatherCall[1] <= 0) &&
					(motherCall[0] <= 0 || motherCall[1] <= 0)) {
				nonPaternal.add(v);
				nonMaternal.add(v);
				// FIXME -these may not be all that interesting if one of the parents was already homalt
				// FIXME -this leads to duplicate values in the MendRecHet fields when these vars are paired with denovo
			} else {
				for (int allele: childCall) {
					if (allele > 0) { // only care about transmission of alt alleles
						boolean notInFather = indexOf(allele, fatherCall) < 0;
						boolean notInMother = indexOf(allele, motherCall) < 0;
						if (notInFather && notInMother) {
							deNovo.add(v);
						} else if (notInFather) {
							nonPaternal.add(v);
						} else if (notInMother) {
							nonMaternal.add(v);
						}
					}
				}
			}
		}
		if ((!nonPaternal.isEmpty() && !nonMaternal.isEmpty()) ||
				deNovo.size() > 1 || deNovo.size() * (nonMaternal.size() + nonPaternal.size()) > 0) {
			for (VCFVariant v : deNovo) {
				ArrayList<String> indices = new ArrayList<String>(); // TODO -initial size
				for (VCFVariant paired_variant : nonPaternal) {
					indices.add(paired_variant.getInfoValue("Index"));
				}
				for (VCFVariant paired_variant : nonMaternal) {
					indices.add(paired_variant.getInfoValue("Index"));
				}
				for (VCFVariant paired_variant : deNovo) {
					if (paired_variant != v) indices.add(paired_variant.getInfoValue("Index"));
				}
				if (!indices.isEmpty()) {
					v.putInfoFlag("COMPOUND");
					v.putInfo("MendHetRec", indices.toArray(new String[indices.size()]));
				}
			}
			for (VCFVariant v : nonPaternal) {
				ArrayList<String> indices = new ArrayList<String>(); // TODO -initial size
				for (VCFVariant paired_variant : nonMaternal) {
					indices.add(paired_variant.getInfoValue("Index"));
				}
				for (VCFVariant paired_variant : deNovo) {
					indices.add(paired_variant.getInfoValue("Index"));
				}
				if (!indices.isEmpty()) {
					v.putInfoFlag("COMPOUND");
					v.putInfo("MendHetRec", indices.toArray(new String[indices.size()]));
				}
			}
			for (VCFVariant v : nonMaternal) {
				ArrayList<String> indices = new ArrayList<String>(); // TODO -initial size
				for (VCFVariant paired_variant : nonPaternal) {
					indices.add(paired_variant.getInfoValue("Index"));
				}
				for (VCFVariant paired_variant : deNovo) {
					indices.add(paired_variant.getInfoValue("Index"));
				}
				if (!indices.isEmpty()) {
					v.putInfoFlag("COMPOUND");
					v.putInfo("MendHetRec", indices.toArray(new String[indices.size()]));
				}
			}
		}
		filteredVariants = groupedVariants.iterator();
	}
	
	@Override
	public boolean hasNext() {
		return filteredVariants.hasNext() || grouper.hasNext();
	}

	@Override
	public VCFVariant next() {
		if (!filteredVariants.hasNext()) {
			advanceGroup();
		}
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
		BufferedReader br = GunzipIfGZipped.filenameToBufferedReader(argv[0]);
		VCFParser p = new VCFParser(br);
		VCFHeaders headers = p.getHeaders();
		headers.addAll(Arrays.asList(ADDITIONAL_HEADERS));
		System.out.print(headers);
		System.out.println(headers.getColumnHeaderLine());
		
		int yes = 0, no = 0;
		for (CompoundMutationFilter cmf = new CompoundMutationFilter(p, VCFUtils.getTrioIndices(p.getHeaders()).get(0)); cmf.hasNext();) {
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

}
