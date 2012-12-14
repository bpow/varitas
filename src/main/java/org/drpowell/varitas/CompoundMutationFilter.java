package org.drpowell.varitas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.drpowell.util.Grouper;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;

public class CompoundMutationFilter implements Iterator<VCFVariant> {
	
	private final Grouper<String, VCFVariant> grouper;
	private Iterator<VCFVariant> filteredVariants;
	private final int [] trioIndices;

	public CompoundMutationFilter(Iterator<VCFVariant> delegate, int [] trioIndices) {
		this.trioIndices = trioIndices;
		grouper = new VCFGeneGrouper().setDelegate(delegate);
		advanceGroup();
	}

	// FIXME - handle multiple trios somehow...
	public CompoundMutationFilter(Iterator<VCFVariant> delegate, VCFHeaders headers) {
		this(delegate, VCFUtils.getTrioIndices(headers).get(0));
	}
	
	private void advanceGroup() {
		// time to get the next group of variants...
		Collection<VCFVariant> groupedVariants = grouper.next();
		
		ArrayList<VCFVariant> variantsNotInAParent = new ArrayList<VCFVariant>(groupedVariants.size());
		int transmitted[] = {0, 0};
		for (VCFVariant v : groupedVariants) {
			String [] calls = v.getCalls();
			int alt0 = countAltAlleles(calls[trioIndices[0]]);
			if (alt0 == 0) {
				continue;
				// proband unknown or homozygous reference
			}
			int alt1 = countAltAlleles(calls[trioIndices[1]]), alt2 = countAltAlleles(calls[trioIndices[2]]);
			if (alt0 == 2) {
				if (alt1 < 2 && alt2 < 2) {
					transmitted[0]++;
					transmitted[1]++;
					variantsNotInAParent.add(v);
				}
			} else if (alt0 == alt1 && alt0 != alt2) {
				transmitted[0]++;
				variantsNotInAParent.add(v);
			} else if (alt0 == alt2 && alt0 != alt1) {
				transmitted[1]++;
				variantsNotInAParent.add(v);
			}
		}
		if (transmitted[0] != 0 && transmitted[1] != 0) {
			for (int i = variantsNotInAParent.size() - 1; i >= 0; i--) {
				variantsNotInAParent.get(i).putInfo("BIALLELIC", null);
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
			String gene = v.getInfoField("Gene_name");
			if (gene.isEmpty()) gene = null;
			return gene;
		}
	}
	
	private static final int countAltAlleles(String call) {
		int altCount = 0;
		for (int i = 0, n = call.length(); i < n; i++) {
			switch (call.charAt(i)) {
			case ':':
				return altCount;
			case '0':
			case '.':
			case '/':
			case '|':
				break;
			default:
				altCount++;
				break;
			}
		}
		return altCount;
	}
	
	public static void main(String argv[]) throws IOException {
		VCFParser p = new VCFParser(argv[0]);
		
		int yes = 0, no = 0;
		for (CompoundMutationFilter cmf = new CompoundMutationFilter(p.iterator(), p.getHeaders()); cmf.hasNext();) {
			VCFVariant v = cmf.next();
			if (v.hasInfo("BIALLELIC")) {
				System.out.println(v);
				yes++;
			} else {
				no++;
			}
		}
		System.out.println(String.format("%d biallelic mutations,  %d otherwise", yes, no));
	}

}
