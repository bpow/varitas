package org.drpowell.varitas;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.drpowell.util.FilteringIterator;
import org.drpowell.util.GunzipIfGZipped;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;

public class MendelianConstraintFilter extends FilteringIterator<VCFVariant> {

	private List<int []> trios;
	
	public static VCFMeta[] ADDITIONAL_HEADERS = {
			VCFParser.parseVCFMeta("##INFO=<ID=MVCLR,Number=1,Type=Float,Description=\"Log-likelihood ratio of most likely unconstrained to constrained genotype\">"),
			VCFParser.parseVCFMeta("##INFO=<ID=MENDELLR,Number=1,Type=Float,Description=\"Log-likelihood ratio of unconstrained to constrained genotypes\">"),
			VCFParser.parseVCFMeta("##INFO=<ID=UNCGT,Number=1,Type=String,Description=\"Most likely unconstrained trio genotypes\">"),
			VCFParser.parseVCFMeta("##INFO=<ID=CONGT,Number=1,Type=String,Description=\"Most likely genotypes under mendelian constraints\">"),
			VCFParser.parseVCFMeta("##INFO=<ID=TMV,Number=0,Type=Flag,Description=\"The most-likely individual calls show a mendelian violation among a trio.\">")
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
	
	private boolean singleAllele(int genotype) {
		//return Integer.bitCount(genotype) == 1;
		return (genotype > 0) && ((genotype & (genotype-1)) == 0);
	}
	
	private final int [] phaseTrio(String cgt, String fgt, String mgt) {
		return phaseTrio(genotypeToAlleleBitmap(cgt),
						 genotypeToAlleleBitmap(fgt),
						 genotypeToAlleleBitmap(mgt));
	}
	
	private final int [] phaseTrio(int ca, int fa, int ma) {
		// fixme -- can sometime phase parent/child even if other parent's data is missing
		int [] phase = null;
		if ( (((fa|ma)&ca) == ca) && (fa&ca) > 0 && (ma&ca) > 0 )  {
			// OK by mendelian...
			phase = new int[3];
			//if ((ca&fa&ma) == 0 || singleAllele(ca&fa&ma)) (phaseable) 
			if (singleAllele(ca)) { // GENOTYPE_CARDINALITY[c] == 1
				// child homozygous
				phase[0] = 1;
				phase[1] = (fa^ca) >= ca ? 1 : -1;
				phase[2] = (ma^ca) >= ca ? 1 : -1;
			} else {
				int father_transmitted = 0;
				int mother_transmitted = 0;
				if (singleAllele(fa&ca)) {
					// only one allele could have come from father
					father_transmitted = ca&fa;
					mother_transmitted = ca^father_transmitted;
				} else if (singleAllele(ma&ca)) {
					// only one allele could have come from mother
					mother_transmitted = ca&ma;
					father_transmitted = ca^mother_transmitted;
				}
				if (mother_transmitted > 0 && father_transmitted > 0) {
					phase[0] = father_transmitted <= mother_transmitted ? 1 : -1;
					phase[1] = father_transmitted <= (father_transmitted ^ fa) ? 1 : -1;
					phase[2] = mother_transmitted <= (mother_transmitted ^ ma) ? 1 : -1;
				} else {
					// not phaseable
					phase[0] = phase[1] = phase[2] = 0;
				}
			}
		} else {
			// Mendelian violation! Will return null!
		}
		return phase;
	}
	
	/**
	 * executed for its side effect of annotating with constrained vs. unconstrained likelihood ratio
	 * 
	 * @param variant
	 * @return
	 */
	@Override
	public VCFVariant filter(VCFVariant variant) {
		// FIXME - have option to only return variants with at least one MV
		double [][] logLikelihoods = variant.getGenotypeLikelihoods();
		TRIO:
		for (int [] trio : trios) {
			// FIXME - can sometimes phase when a member of trio is missing
			if (trio[0] < 0 || trio[1] < 0 || trio[2] < 0) continue;
			
			// check that we will have all of the likelihoods we will need
			double [][] trioLL = new double[trio.length][];
			// 0: child    1: father    2:mother
			for (int i = 0; i < trioLL.length; i++) {
				if (null == logLikelihoods || trio[i] >= logLikelihoods.length || (trioLL[i] = logLikelihoods[trio[i]]) == null) {
					// no likelihood data for this sample
					// let's try to phase anyway...
					int [] phases = phaseTrio(variant.getGenotype(trio[0]),
											  variant.getGenotype(trio[1]),
											  variant.getGenotype(trio[2]));
					if (phases == null) {
						variant.putInfoFlag("TMV");
					} else {
						variant.setPhases(trio, phases);
					}
					continue TRIO;
				}
			}
			
			double maxUnconstrained = Double.NEGATIVE_INFINITY;
			int [] gtUnconstrained = {0, 0, 0};
			double maxConstrained = Double.NEGATIVE_INFINITY;
			int [] gtConstrained = {0, 0, 0};
			int [] phase = {0, 0, 0}; // for a|b, -1 => b<a, 0 => unphased, 1 => a<=b
			
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
								phase = phaseTrio(ca, fa, ma); // will not be null because we already checked above
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
				variant.putInfo("MVCLR", String.format("%.3g", maxUnconstrained - maxConstrained));
				// FIXME-- this is not doing what I think it should...
				variant.putInfo("MENDELLR", String.format("%.3g", calcLogLikelihoodRatio(constrainedLikelihoods, unconstrainedLikelihoods)));
				variant.putInfo("UNCGT", getGenotypes(gtUnconstrained, null));
				variant.putInfo("CONGT", getGenotypes(gtConstrained, phase));
				variant.putInfoFlag("TMV");
			} else {
				variant = variant.setPhases(trio, phase);
			}
		}
		return variant; // FIXME - just returning all variants for now, consider returning only phased or MV
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

	private String [] getGenotypes(int[] genotypePLindices, int [] phase) {
		String [] gts = new String[genotypePLindices.length];
		for (int i = 0; i < genotypePLindices.length; i++) {
			if (phase != null && i < phase.length) {
				gts[i] = plIndexToAlleles(genotypePLindices[i], phase[i]);
			} else {
				gts[i] = plIndexToAlleles(genotypePLindices[i], 0);
			}
		}
		return gts;
	}
	
	private final int genotypeToAlleleBitmap(String gt) {
		if (gt.startsWith(".") || gt.endsWith(".")) return -1;
		int delim = gt.indexOf('|');
		if (delim < 0) delim = gt.indexOf('/');
		if (delim < 0) return -1;
		int alleles = 0;
		try {
			alleles |= 1 << Integer.parseInt(gt.substring(0, delim));
			alleles |= 1 << Integer.parseInt(gt.substring(delim+1));			
		} catch (NumberFormatException nfe) {
			Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error parsing genotype: [ " + gt + " ]", nfe);
			return -1;
		}
		return alleles;
	}

	private final String plIndexToAlleles(int i, int phase) {
		int k = (int) Math.floor(triangularRoot(i));
		int j = i - (k * (k + 1)) / 2;
		if (phase == 0) {
			return Integer.toString(j) + "/" + Integer.toString(k);
		}
		if (phase < 0) {
			j ^= k; k ^= j; j ^= k; // obscure swap
		}
		return Integer.toString(j) + "|" + Integer.toString(k);
	}
	
	private final double triangularRoot(double x) {
		return (Math.sqrt(8.0 * x + 1) - 1) / 2.0;
	}
	
	public static void main(String argv[]) throws IOException {
		BufferedReader br = GunzipIfGZipped.filenameToBufferedReader(argv[0]);
		VCFParser p = new VCFParser(br);
		VCFHeaders h = p.getHeaders();
		for (VCFMeta m : ADDITIONAL_HEADERS) { h.add(m); }
		System.out.print(h.toString());
		System.out.println(h.getColumnHeaderLine());
		
		int yes = 0, no = 0;
		for (MendelianConstraintFilter mcf = new MendelianConstraintFilter(p.iterator(), p.getHeaders());
				mcf.hasNext();) {
			VCFVariant v = mcf.next();
			if (v.hasInfo("MENDELLR")) {
				System.out.println(v);
				yes++;
			} else {
				System.out.println(v);
				no++;
			}
		}
		br.close();
		System.err.println(String.format("%d mendelian violations,  %d otherwise", yes, no));
	}


}
