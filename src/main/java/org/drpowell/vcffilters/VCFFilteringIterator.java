package org.drpowell.vcffilters;

import java.util.Iterator;

import org.drpowell.util.FilteringIterator;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFVariant;

public abstract class VCFFilteringIterator extends FilteringIterator<VCFVariant> implements VCFIterator {
	protected VCFHeaders originalHeaders;

	public VCFFilteringIterator(Iterator<VCFVariant> client, VCFHeaders headers) {
		super(client);
		this.setOriginalHeaders(headers);
	}
	
	public VCFFilteringIterator(VCFIterator client) {
		this(client, client.getHeaders());
	}

	protected void setOriginalHeaders(VCFHeaders headers) {
		originalHeaders = headers;
	}
	
	public VCFHeaders getHeaders() {
		return originalHeaders;
	}

}
