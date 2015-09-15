package org.drpowell.vcf;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;


public class VariantContextReaderIterator implements VariantContextIterator {
    private final CloseableIterator<VariantContext> iterator;
    private final VCFFileReader reader;

    public VariantContextReaderIterator(VCFFileReader reader) {
        this.reader = reader;
        iterator = reader.iterator();
    }

    @Override
    public VCFHeader getHeader() {
        return reader.getFileHeader();
    }

    @Override
    public void close() {
        iterator.close();
        reader.close();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public VariantContext next() {
        return iterator.next();
    }
}
