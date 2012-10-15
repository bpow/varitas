package org.drpowell.varitas;

import org.drpowell.vcf.VCFVariant;


public abstract class Annotator {
	
	protected int refColumn = -1;
	protected int altColumn = -1;
	protected String prefix = "";
	
	/**
	 * Provide a column number which is the "reference" call at a locus, for checking in the annotation process.
	 * Coordinates are 1-based (i.e. the 1st column is column #1.
	 * 
	 * If the argument altColumn is less than or equal to 0, then no checking will be performed.
	 * 
	 * @param refColumn
	 * @return this, so you can chain calls
	 */
	public Annotator checkRef(int refColumn) {
		this.refColumn = refColumn-1;
		return this;
	}

	/**
	 * Provide a column number which is the "alternate" call at a locus, for checking in the annotation process.
	 * Coordinates are 1-based (i.e. the 1st column is column #1.
	 * 
	 * If the argument altColumn is less than or equal to 0, then no checking will be performed.
	 * 
	 * @param altColumn
	 * @return this, so you can chain calls
	 */
	public Annotator checkAlt(int altColumn) {
		this.altColumn = altColumn-1;
		return this;
	}
	
	/**
	 * Indicate whether the prefix 'chr' needs to be added prior to queries (to allow for ucsc-style chromosome names)
	 * @param addChr boolean
	 * @return this, so you can chain calls
	 */
	public Annotator setAddChr(boolean addChr) {
		prefix = addChr? "chr" : "";
		return this;
	}

	public abstract VCFVariant annotate(VCFVariant var);

	public abstract Iterable<String> infoLines();

}