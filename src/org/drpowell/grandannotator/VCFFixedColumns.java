package org.drpowell.grandannotator;

public enum VCFFixedColumns {
	CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO, FORMAT;
	private static final String VCF_FIXED_COLUMN_HEADER =
			"#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT";
}
