package org.drpowell.varitas;

public enum VCFFixedColumns {
	CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO, FORMAT;
	public static final String VCF_FIXED_COLUMN_HEADERS =
			"#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT";
	public static final int SIZE = values().length;
}
