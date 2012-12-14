package org.drpowell.varitas;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.drpowell.util.FilteringIterator;
import org.drpowell.util.GunzipIfGZipped;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;

public class MendelianConstraintFilter extends FilteringIterator<VCFVariant> {

	private List<int []> trios;
	
	/**
	 * PL and GL fields in VCF/BCF are defined to have ordering:
	 * AA,
	 * AB, BB,
	 * AC, BC, CC,
	 * AD, BD, CD, DD, ...
	 * 
	 * So that genotype (j,k) appears at position k*(k+1)/2 + j
	 *   (with j < k, and zero-based alleles)
	 *   
	 * PL_TO_ALLELES gives the integers where bits are set to reflect the alleles
	 * present in the nth entry of the PL/GL array. This has been precomputed for
	 * up to 16 alleles at a site. Seeing as how I am only analyzing trios for now,
	 * this is overkill.
	 */
	public static final int[] PL_TO_ALLELES = { 1, 3, 2, 5, 6, 4, 9, 10, 12, 8,
		17, 18, 20, 24, 16, 33, 34, 36, 40, 48, 32, 65, 66, 68, 72, 80, 96,
		64, 129, 130, 132, 136, 144, 160, 192, 128, 257, 258, 260, 264,
		272, 288, 320, 384, 256, 513, 514, 516, 520, 528, 544, 576, 640,
		768, 512, 1025, 1026, 1028, 1032, 1040, 1056, 1088, 1152, 1280,
		1536, 1024, 2049, 2050, 2052, 2056, 2064, 2080, 2112, 2176, 2304,
		2560, 3072, 2048, 4097, 4098, 4100, 4104, 4112, 4128, 4160, 4224,
		4352, 4608, 5120, 6144, 4096, 8193, 8194, 8196, 8200, 8208, 8224,
		8256, 8320, 8448, 8704, 9216, 10240, 12288, 8192, 16385, 16386,
		16388, 16392, 16400, 16416, 16448, 16512, 16640, 16896, 17408,
		18432, 20480, 24576, 16384, 32769, 32770, 32772, 32776, 32784,
		32800, 32832, 32896, 33024, 33280, 33792, 34816, 36864, 40960,
		49152, 32768 };
	
	public MendelianConstraintFilter(Iterator<VCFVariant> client, VCFHeaders headers) {
		super(client);
		trios = VCFUtils.getTrioIndices(headers);
	}
	
	/**
	 * executed for its side effect of annotating with constrained vs. unconstrained likelihood ratio
	 * 
	 * @param element
	 * @return
	 */
	@Override
	public boolean filter(VCFVariant element) {
		boolean any = false; // will be set to true if any trios return true;
		// FIXME - have option to only return variants with at least one MV
		String [] calls = element.getCalls();
		int plIndex = findPL(element.getFormat().split(":"));
		if (plIndex < 0) {
			//FIXME - should I log this?
			return true; // or false?
		}
		// FIXME - handle GL instead of PL when available (for freebayes)
		for (int [] trio : trios) {
			if (trio[0] < 0 || trio[1] < 1 || trio[2] < 0) continue;
			int [] childPL = callToPL(calls[trio[0]], plIndex);
			int [] fatherPL = callToPL(calls[trio[1]], plIndex);
			int [] motherPL = callToPL(calls[trio[2]], plIndex);
			if (null == childPL || null == fatherPL || null == motherPL) {
				// at least one of the family members does not have PL information
				element.putInfo("NOPL", null);
				continue;
			}

			long minUnconstrained = Long.MAX_VALUE;
			int [] gtUnconstrained = {0, 0, 0};
			long minConstrained = Long.MAX_VALUE;
			int [] gtConstrained = {0, 0, 0};
			
			ArrayList<Double> constrainedLikelihoods = new ArrayList<Double>(30);
			ArrayList<Double> unconstrainedLikelihoods = new ArrayList<Double>(30);
			
			// FIXME - could skip this for common cases (look if the zeros in plc, plf, plm do not violate mendelian constraints)
			for (int c = 0; c < childPL.length; c++) {
				int ca = PL_TO_ALLELES[c];
				for (int f = 0; f < fatherPL.length; f++) {
					int fa = PL_TO_ALLELES[f];
					for (int m = 0; m < motherPL.length; m++) {
						int ma = PL_TO_ALLELES[m];
						int sum = childPL[c] + fatherPL[f] + motherPL[m];
						if ( (((fa|ma)&ca) == ca) && (fa&ca) > 0 && (ma&ca) > 0 ) {
							// OK by mendelian rules
							// if (sum == 0) return true; // special-case: ML genotype is not mendelian violation
							if (sum < minConstrained) {
								minConstrained = sum;
								gtConstrained[0] = c; gtConstrained[1] = f; gtConstrained[2] = m;
							}
							constrainedLikelihoods.add(sum/-10.0);
						} else {
							// violation
							if (sum < minUnconstrained) {
								minUnconstrained = sum;
								gtUnconstrained[0] = c; gtUnconstrained[1] = f; gtUnconstrained[2] = m;
							}
							unconstrainedLikelihoods.add(sum/10.0);
						}
					}
				}
			}
			// FIXME - need to handle multiple trios better
			if (minConstrained > minUnconstrained) {
				any = true;
				element.putInfo("MVCLR", minConstrained - minUnconstrained);
				// FIXME-- this is not doing what I think it should...
				element.putInfo("MENDELLR", String.format("%.3g", calcLogLikelihoodRatio(constrainedLikelihoods, unconstrainedLikelihoods)));
				element.putInfo("UNCGT", joinGenotypes(gtUnconstrained));
				element.putInfo("CONGT", joinGenotypes(gtConstrained));
			}			
		}
		return true; // should probably be 'return any;'
	}

	private double calcLogLikelihoodRatio(ArrayList<Double> constrainedSums, ArrayList<Double> unconstrainedSums) {
		return log10unionProbabilities(constrainedSums) - log10unionProbabilities(unconstrainedSums);
	}

	private final double mlog10unionProbabilities(ArrayList<Integer> logPs) {
		double sum = 0;
		double max = Double.NEGATIVE_INFINITY;
		for (Integer logP : logPs) {
			if (-logP > max) {
				max = -logP;
			}
		}
		if (Double.NEGATIVE_INFINITY == max) return max;
		for (Integer logP : logPs) {
			sum += Math.pow(10.0, -logP + max);
		}
		return max - Math.log10(sum);
	}

	private final double log10unionProbabilities(ArrayList<Double> logPs) {
		double sum = 0;
		double max = Double.NEGATIVE_INFINITY;
		for (Double logP : logPs) {
			if (logP > max) {
				max = logP;
			}
		}
		if (Double.NEGATIVE_INFINITY == max) return max;
		for (Double logP : logPs) {
			sum += Math.pow(10.0, logP - max);
		}
		return max + Math.log10(sum);
	}

	private String joinGenotypes(int[] genotypePLindices) {
		StringBuffer sb = new StringBuffer(genotypePLindices.length * 4);
		for (int i = 0; i < genotypePLindices.length; i++) {
			sb.append(plIndexToAlleles(genotypePLindices[i])).append(",");
		}
		return sb.substring(0, sb.length()-1);
	}

	private int[] callToPL(String call, int plIndex) {
		String [] splitCalls = call.split(":");
		if (plIndex >= splitCalls.length) {
			return null; // no PL field here!
		}
		String [] pls = splitCalls[plIndex].split(",");
		int [] out = new int[pls.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = Integer.parseInt(pls[i]);
		}
		return out;
	}

	private final int findPL(String[] format) {
		for (int i = 0; i < format.length; i++) {
			if ("PL".equals(format[i])) return i;
		}
		return -1;
	}
	
	private final String plIndexToAlleles(int i) {
		double tr = (int) Math.floor(triangularRoot(i));
		int j = i - (int) tr;
		int k = (int) Math.round((Math.sqrt(1+8*tr)-1)/2);
		return Integer.toString(j) + "/" + Integer.toString(k);
	}
	
	private final double triangularRoot(double x) {
		return (Math.sqrt(8.0 * x + 1) - 1) / 2.0;
	}
	
	public static void main(String argv[]) throws IOException {
		BufferedReader br = GunzipIfGZipped.filenameToBufferedReader(argv[0]);
		VCFParser p = new VCFParser(br);
		
		int yes = 0, no = 0;
		for (MendelianConstraintFilter mcf = new MendelianConstraintFilter(p.iterator(), p.getHeaders());
				mcf.hasNext();) {
			VCFVariant v = mcf.next();
			if (v.hasInfo("MENDELLR")) {
				System.out.println(v);
				yes++;
			} else {
				no++;
			}
		}
		br.close();
		System.out.println(String.format("%d mendelian violations,  %d otherwise", yes, no));
	}


}
