package org.drpowell.vcf;

public class Genotype {
	public final int [] alleles;
	public final boolean phased;
	private static final String MISSING_ALLELE = ".";
	
	private static int toAllele(String s) {
		if (MISSING_ALLELE.equals(s) || s.isEmpty()) return -1;
		return Integer.parseInt(s);
	}
	
	private static int [] toAlleles(String... s) {
		int [] out = new int[s.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = toAllele(s[i]);
		}
		return out;
	}
	
	private static String alleleToString(int allele) {
		if (allele < 0) return MISSING_ALLELE;
		return Integer.toString(allele);
	}
	
	public static Genotype parseVCFGenotype(String s) {
		// first assume the common cases of haploid or diploid
		boolean tPhased = false;
		int delim = s.indexOf('/');
		if (delim < 0) {
			tPhased = true;
			delim = s.indexOf('|');
		}
		if (delim < 0) {
			// single allele (or nonstandard delimiter... will throw an exception if the latter)
			int [] tAlleles = {toAllele(s)};
			return new Genotype(tAlleles, true);
		}
		if (s.indexOf('/', delim+1) < 0 && s.indexOf('|', delim+1) < 0) {
			// diploid
			return new Genotype(toAlleles(s.substring(0, delim), s.substring(delim+1)), tPhased);
		}
		// FIXME - handle partially-phased genotypes?
		tPhased = s.indexOf('/') >= 0;
		return new Genotype(toAlleles(s.split("[|/]")), tPhased);
	}

	public String toString() {
		switch (alleles.length) {
		case 0:
			return "";
		case 1:
			return alleleToString(alleles[0]);
		case 2:
			return alleleToString(alleles[0]) + (phased ? "|" : "/") + alleleToString(alleles[1]);
		default:
			char delimiter = phased ? '|' : '/';
			StringBuilder sb = new StringBuilder();
			for (int allele : alleles) {
				sb.append(alleleToString(allele)).append(delimiter);
			}
			return sb.substring(0, sb.length()-1);
		}
	}
	
	protected Genotype(int [] alleles, boolean phased) {
		this.alleles = alleles;
		this.phased = phased;
	}
}
