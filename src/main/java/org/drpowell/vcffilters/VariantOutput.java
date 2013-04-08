package org.drpowell.vcffilters;

import org.drpowell.vcf.VCFIterator;


/**
 * A tagging interface for VCFIterators that will produce output (.vcf, .xls, etc) as
 * a side effect.
 * 
 * This is potentially useful if you want to check that there is something recording
 * output at the end of a filtering chain.
 * 
 * @author bpow
 *
 */
public interface VariantOutput extends VCFIterator {
}
