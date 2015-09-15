package org.drpowell.varitas;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.drpowell.vcf.VariantContextIterator;

public class AnnotatingIterator implements VariantContextIterator {
	private final Annotator annotator;
	private final VariantContextIterator client;
	
	public AnnotatingIterator(VariantContextIterator client, Annotator annotator) {
		this.annotator = annotator;
		this.client = client;
	}

	@Override
	public boolean hasNext() {
		return client.hasNext();
	}

	@Override
	public VariantContext next() {
		return annotator.annotate(client.next());
	}

	@Override
	public void remove() {
		client.remove();
	}

	@Override
	public VCFHeader getHeader() {
		VCFHeader headers = new VCFHeader(client.getHeader());
		for (VCFInfoHeaderLine info : annotator.infoLines()) {
			headers.addMetaDataLine(info);
		}
		return headers;
	}

	@Override
	public void close() {
		client.close();
	}
}
