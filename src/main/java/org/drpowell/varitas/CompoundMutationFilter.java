package org.drpowell.varitas;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.drpowell.util.Grouper;
import org.drpowell.util.GunzipIfGZipped;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;

public class CompoundMutationFilter implements Iterator<VCFVariant> {
	
	private final Grouper<String, VCFVariant> grouper;
	private Iterator<VCFVariant> filteredVariants;
	private final int [] trioIndices;
	public static final VCFMeta ADDITIONAL_HEADER = VCFParser.parseVCFMeta("##INFO=<ID=BIALLELIC,Number=0,Type=Flag,Description=\"Within this gene there is at least one variant inherited from each parent.\">");

	public CompoundMutationFilter(Iterator<VCFVariant> delegate, int [] trioIndices) {
		this.trioIndices = trioIndices;
		grouper = new VCFGeneGrouper().setDelegate(delegate);
		advanceGroup();
	}

	// FIXME - handle multiple trios somehow...
	public CompoundMutationFilter(Iterator<VCFVariant> delegate, VCFHeaders headers) {
		this(delegate, VCFUtils.getTrioIndices(headers).get(0));
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
		for (VCFVariant v : groupedVariants) {
			String [] calls = v.getCalls();
			int [] childCall = splitAlleles(calls[trioIndices[0]]);
			if (childCall[0] <= 0 && childCall[1] <= 0) {
				continue;
				// proband unknown or homozygous reference
			}
			if (childCall[0] > 0 && childCall[1] > 0) {
				nonPaternal.add(v);
				nonMaternal.add(v);
				// FIXME -these may not be all that interesting if one of the parents was already homalt
			} else {
				int [] fatherCall = splitAlleles(calls[trioIndices[1]]);
				int [] motherCall = splitAlleles(calls[trioIndices[2]]);
				for (int allele: childCall) {
					if (allele > 0) { // only care about transmission of alt alleles
						if (indexOf(allele, fatherCall) < 0) {
							nonPaternal.add(v);
						}
						if (indexOf(allele, motherCall) < 0) {
							nonMaternal.add(v);
						}
					}
				}
			}
		}
		if (!nonPaternal.isEmpty() && !nonMaternal.isEmpty()) {
			for (VCFVariant v : nonPaternal) {
				v.putInfo("BIALLELIC", (String []) null);
			}
			for (VCFVariant v : nonMaternal) {
				v.putInfo("BIALLELIC", (String []) null);
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
		headers.add(ADDITIONAL_HEADER);
		System.out.print(headers);
		System.out.println(headers.getColumnHeaderLine());
		
		int yes = 0, no = 0;
		for (CompoundMutationFilter cmf = new CompoundMutationFilter(p.iterator(), headers); cmf.hasNext();) {
			VCFVariant v = cmf.next();
			if (v.hasInfo("BIALLELIC")) {
				System.out.println(v);
				yes++;
			} else {
				no++;
			}
		}
		br.close();
		System.err.println(String.format("%d biallelic mutations,  %d otherwise", yes, no));
	}

}
