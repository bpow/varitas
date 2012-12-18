package org.drpowell.varitas;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.drpowell.util.FilteringIterator;
import org.drpowell.util.GunzipIfGZipped;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;

public class MendelianConstraintFilter extends FilteringIterator<VCFVariant> {

	private List<int []> trios;
	
	private static VCFMeta[] additionalHeaders = {
			VCFParser.parseVCFMeta("##INFO=<ID=MVCLR,Number=1,Type=Float,Description=\"Log-likelihood ratio of most likely unconstrained to constrained genotype\">"),
			VCFParser.parseVCFMeta("##INFO=<ID=MENDELLR,Number=1,Type=Float,Description=\"Log-likelihood ratio of unconstrained to constrained genotypes\">"),
			VCFParser.parseVCFMeta("##INFO=<ID=UNCGT,Number=1,Type=String,Description=\"Most likely unconstrained trio genotypes\">"),
			VCFParser.parseVCFMeta("##INFO=<ID=CONGT,Number=1,Type=String,Description=\"Most likely genotypes under mendelian constraints\">")			
	};
	
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
	 * GENOTYPE_INDEX gives the integers where bits are set to reflect the alleles
	 * present in the nth entry of the PL/GL array. This has been precomputed for
	 * up to 16 alleles at a site. Seeing as how I am only analyzing trios for now,
	 * this is overkill.
	 */
	public static final int[] GENOTYPE_INDEX = { 1, 3, 2, 5, 6, 4, 9, 10, 12, 8,
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
		double [][] logLikelihoods = element.getGenotypeLikelihoods();
		if (null == logLikelihoods) return true; // no likelihood info, just pass on through. FIXME-- decide whether to pass or fail
		TRIO:
		for (int [] trio : trios) {
			if (trio[0] < 0 || trio[1] < 0 || trio[2] < 0) continue;
			
			// check that we will have all of the likelihoods we will need
			double [][] trioLL = new double[trio.length][];
			// 0: child    1: father    2:mother
			for (int i = 0; i < trioLL.length; i++) {
				if (trio[i] >= logLikelihoods.length || (trioLL[i] = logLikelihoods[trio[i]]) == null) {
					// no likelihood data for this sample
					element.putInfo("NOPL", null);
					continue TRIO;
				}
			}
			
			double maxUnconstrained = Double.NEGATIVE_INFINITY;
			int [] gtUnconstrained = {0, 0, 0};
			double maxConstrained = Double.NEGATIVE_INFINITY;
			int [] gtConstrained = {0, 0, 0};
			
			ArrayList<Double> constrainedLikelihoods = new ArrayList<Double>(30);
			ArrayList<Double> unconstrainedLikelihoods = new ArrayList<Double>(30);
			
			// FIXME - could skip this for common cases (look if the zeros in plc, plf, plm do not violate mendelian constraints)
			for (int c = 0; c < trioLL[0].length; c++) {
				int ca = GENOTYPE_INDEX[c];
				for (int f = 0; f < trioLL[1].length; f++) {
					int fa = GENOTYPE_INDEX[f];
					for (int m = 0; m < trioLL[2].length; m++) {
						int ma = GENOTYPE_INDEX[m];
						double sum = trioLL[0][c] + trioLL[1][f] + trioLL[2][m];
						if ( (((fa|ma)&ca) == ca) && (fa&ca) > 0 && (ma&ca) > 0 ) {
							// OK by mendelian rules
							// if (sum == 0) return true; // special-case: ML genotype is not mendelian violation
							if (sum > maxConstrained) {
								maxConstrained = sum;
								gtConstrained[0] = c; gtConstrained[1] = f; gtConstrained[2] = m;
							}
							constrainedLikelihoods.add(sum);
						} else {
							// violation
							if (sum > maxUnconstrained) {
								maxUnconstrained = sum;
								gtUnconstrained[0] = c; gtUnconstrained[1] = f; gtUnconstrained[2] = m;
							}
							unconstrainedLikelihoods.add(sum);
						}
					}
				}
			}
			// FIXME - need to handle multiple trios better
			if (maxConstrained < maxUnconstrained) {
				any = true;
				element.putInfo("MVCLR", String.format("%.3g", maxUnconstrained - maxConstrained));
				// FIXME-- this is not doing what I think it should...
				element.putInfo("MENDELLR", String.format("%.3g", calcLogLikelihoodRatio(constrainedLikelihoods, unconstrainedLikelihoods)));
				element.putInfo("UNCGT", joinGenotypes(gtUnconstrained));
				element.putInfo("CONGT", joinGenotypes(gtConstrained));
			}			
		}
		return true; // should probably be 'return any;'
	}

	private double calcLogLikelihoodRatio(ArrayList<Double> constrainedSums, ArrayList<Double> unconstrainedSums) {
		return logSumOfLogs(unconstrainedSums) - logSumOfLogs(constrainedSums);
	}

	private final double logSumOfLogs(ArrayList<Double> logPs) {
		if (logPs.size() == 1) return logPs.get(0);
		// to enhance numerical stability, normalize to the maximum value
		double sum = 0;
		double max = Double.NEGATIVE_INFINITY;
		for (Double logP : logPs) {
			if (logP > max) {
				max = logP;
			}
		}
		if (Double.NEGATIVE_INFINITY == max) return max;
		for (Double logP : logPs) {
			if (Double.NEGATIVE_INFINITY != logP) sum += Math.pow(10.0, logP - max);
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