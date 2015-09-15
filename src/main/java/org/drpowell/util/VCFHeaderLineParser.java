package org.drpowell.util;

import htsjdk.variant.vcf.*;

import java.util.Arrays;


public class VCFHeaderLineParser {
    private final VCFHeaderVersion version;

    public VCFHeaderLineParser(final VCFHeaderVersion version) {
        this.version = version;
    }

    public VCFHeaderLine headerFromString(String str) {

        if ( str.startsWith(VCFConstants.INFO_HEADER_START) ) {
            return new VCFInfoHeaderLine(str.substring(7), version);
        } else if ( str.startsWith(VCFConstants.FILTER_HEADER_START) ) {
            return new VCFFilterHeaderLine(str.substring(9), version);
        } else if ( str.startsWith(VCFConstants.FORMAT_HEADER_START) ) {
            return new VCFFormatHeaderLine(str.substring(9), version);
        } else if ( str.startsWith(VCFConstants.CONTIG_HEADER_START) ) {
            throw new IllegalArgumentException("Cannot add a contig header using VCFHeaderLineParser");
        } else if ( str.startsWith(VCFConstants.ALT_HEADER_START) ) {
            return new VCFSimpleHeaderLine(str.substring(6), version, VCFConstants.ALT_HEADER_START.substring(2), Arrays.asList("ID", "Description"));
        } else {
            int equals = str.indexOf("=");
            if ( equals != -1 )
                return new VCFHeaderLine(str.substring(2, equals), str.substring(equals+1));
            else
                throw new IllegalArgumentException("Unable to parse '" + str + "' as a VCF header line");
        }
    }
}
