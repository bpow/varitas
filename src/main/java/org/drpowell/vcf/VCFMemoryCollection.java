package org.drpowell.vcf;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;

public class VCFMemoryCollection extends AbstractList<VariantContext> {
	private final ArrayList<VariantContext> variants = new ArrayList<VariantContext>();
	private VCFHeader headers;
	
	public VCFMemoryCollection(VariantContextIterator input) {
		while (input.hasNext()) variants.add(input.next());
		headers = input.getHeader();
	}
	
	public VCFHeader getHeaders() {
		return headers;
	}

	@Override
	public Iterator<VariantContext> iterator() {
		return variants.iterator();
	}

	@Override
	public int size() {
		return variants.size();
	}

	@Override
	public VariantContext get(int index) {
		return variants.get(index);
	}

}
