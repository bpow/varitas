package org.drpowell.varitas;

import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFVariant;

public class AnnotatingIterator implements VCFIterator {
	private final Annotator annotator;
	private final VCFIterator client;
	
	public AnnotatingIterator(VCFIterator client, Annotator annotator) {
		this.annotator = annotator;
		this.client = client;
	}

	@Override
	public boolean hasNext() {
		return client.hasNext();
	}

	@Override
	public VCFVariant next() {
		return annotator.annotate(client.next());
	}

	@Override
	public void remove() {
		client.remove();
	}

	@Override
	public VCFHeaders getHeaders() {
		VCFHeaders headers = new VCFHeaders(client.getHeaders());
		for (String info : annotator.infoLines()) {
			headers.add(new VCFMeta(info));
		}
		return headers;
	}

}
