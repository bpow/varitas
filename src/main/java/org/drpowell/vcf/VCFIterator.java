package org.drpowell.vcf;

import java.util.Iterator;

/**
 * An iterator over VCFVariants which also makes VCFHeaders available.
 * @author bpow
 *
 */
public interface VCFIterator extends Iterator<VCFVariant> {

	public VCFHeaders getHeaders();

}
