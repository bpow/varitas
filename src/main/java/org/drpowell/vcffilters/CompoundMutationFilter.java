package org.drpowell.vcffilters;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.drpowell.util.FileUtils;
import org.drpowell.util.Grouper;
import org.drpowell.vcf.VCFMemoryCollection;
import org.drpowell.vcf.VariantContextIterator;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

public class CompoundMutationFilter implements VariantContextIterator {

    private static LinkedHashMap<String, String> makeHashMap(String... strings) {
        if (strings.length % 2 != 0) {
            throw new IllegalArgumentException("makeHashMap called with odd number of arguments");
        }
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i < strings.length; i += 2) {
            map.put(strings[i], strings[i + 1]);
        }
        return map;
    }

    // This needs some special handling because the exons of gene can rarely
    // interleave with each other -- so streamed processing would be complicated.
    // I'll just read all of the variants into memory instead...

    private VCFMemoryCollection collectedVariants;
    private Iterator<VariantContext> filteredVariants;
    private final int[][] allTrioIndices;
    private int variantIndex = -1;
    private final VCFHeader header;
    private static final VCFInfoHeaderLine[] ADDITIONAL_HEADERS = {
            new VCFInfoHeaderLine("COMPOUND", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "Within this gene there is at least one variant not inherited from each parent of the listed individual.")
            , new VCFInfoHeaderLine("RecessiveIndex", 1, VCFHeaderLineType.Integer, "Index of the variant within this file (used to refer between variants).")
            , new VCFInfoHeaderLine("RecessivePartners", VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String, "List of indices participating in compound recessive grouping, formatted like: SAMPLE:id0|id2|id2.")
    };

    // FIXME - handle multiple trios somehow...
    public CompoundMutationFilter(VariantContextIterator delegate, List<int[]> trioIndices) {
        allTrioIndices = new int[trioIndices.size()][3];
        int i = 0;
        for (int[] ti : trioIndices) {
            allTrioIndices[i] = ti;
            i++;
        }
        this.header = new VCFHeader(delegate.getHeader());
        for (VCFInfoHeaderLine header : ADDITIONAL_HEADERS) {
            this.header.addMetaDataLine(header);
        }
        collectedVariants = new VCFMemoryCollection(delegate);
        HashMap<String, PhaseGroup[]> phaseGroups = buildPhaseGroups(collectedVariants);
        assignCompoundGroups(phaseGroups);
        filteredVariants = collectedVariants.iterator();
    }

    private PhaseGroup[] getDefaultPhaseGroup(HashMap<String, PhaseGroup[]> map, String key) {
        PhaseGroup[] pg = map.get(key);
        if (pg == null) {
            pg = new PhaseGroup[allTrioIndices.length];
            for (int i = 0; i < pg.length; i++) {
                pg[i] = new PhaseGroup();
            }
            map.put(key, pg);
        }
        return pg;
    }

    private HashMap<String, PhaseGroup[]> buildPhaseGroups(Iterable<VariantContext> variants) {
        HashMap<String, PhaseGroup[]> phaseGroups = new HashMap<String, PhaseGroup[]>();

        for (VariantContext v : variants) {
            for (int trio = 0; trio < allTrioIndices.length; trio++) {
                int[] trioIndices = allTrioIndices[trio];
                // FIXME - use predetermined phase information if available

                // weird negative thinking-- these lists keep track of sites that have a variant that _didn't_ come from either dad or mom
                variantIndex++;
                v.getCommonInfo().putAttribute("Index", Integer.toString(variantIndex));

                Genotype childCall = v.getGenotype(trioIndices[0]);
                if (childCall.isNoCall() || childCall.isHomRef()) {
                    continue;
                }
                PhaseGroup pg = getDefaultPhaseGroup(phaseGroups, v.getAttributeAsString("Gene_name", null))[trio];
                Genotype fatherCall = v.getGenotype(trioIndices[1]);
                Genotype motherCall = v.getGenotype(trioIndices[2]);
                for (Allele allele : childCall.getAlleles()) {
                    if (!allele.isReference()) { // only keep track of transmission of alt alleles
                        boolean notInFather = fatherCall.getAlleles().contains(allele);
                        boolean notInMother = motherCall.getAlleles().contains(allele);
                        if (notInFather && notInMother) {
                            pg.deNovo.add(v);
                        } else if (notInFather) {
                            pg.nonPaternal.add(v);
                        } else if (notInMother) {
                            pg.nonMaternal.add(v);
                        }
                    }
                }
            }
        }
        return phaseGroups;
    }

    private void assignCompoundGroups(Map<String, PhaseGroup[]> phaseGroups) {
        for (PhaseGroup[] pgs : phaseGroups.values()) {
            for (int trio = 0; trio < allTrioIndices.length; trio++) {
                String childName = header.getSampleNamesInOrder().get(allTrioIndices[trio][0]);
                PhaseGroup pg = pgs[trio];
                ArrayList<VariantContext> nonMaternal = pg.nonMaternal;
                ArrayList<VariantContext> nonPaternal = pg.nonPaternal;
                ArrayList<VariantContext> deNovo = pg.deNovo;

                if ((!nonPaternal.isEmpty() && !pg.nonMaternal.isEmpty())
                        || deNovo.size() > 1
                        || pg.deNovo.size() * (nonMaternal.size() + nonPaternal.size()) > 0) {
                    for (VariantContext v : deNovo) {
                        ArrayList<String> indices = new ArrayList<String>(); // TODO - initial size
                        for (VariantContext paired_variant : nonPaternal) {
                            indices.add(paired_variant.getAttributeAsString("Index", ""));
                        }
                        for (VariantContext paired_variant : nonMaternal) {
                            indices.add(paired_variant.getAttributeAsString("Index", ""));
                        }
                        for (VariantContext paired_variant : deNovo) {
                            if (paired_variant != v)
                                indices.add(paired_variant
                                        .getAttributeAsString("Index", ""));
                        }
                        if (!indices.isEmpty()) {
                            v.addInfo("COMPOUND", childName);
                            v.putInfo("RecessivePartners",
                                    String.format("%s:%s", childName, join("|", indices.toArray(new String[indices.size()]))));
                        }
                    }
                    for (VariantContext v : nonPaternal) {
                        ArrayList<String> indices = new ArrayList<String>(); // TODO - initial size
                        for (VariantContext paired_variant : nonMaternal) {
                            indices.add(paired_variant.getInfoValue("Index"));
                        }
                        for (VariantContext paired_variant : deNovo) {
                            indices.add(paired_variant.getInfoValue("Index"));
                        }
                        if (!indices.isEmpty()) {
                            v.addInfo("COMPOUND", childName);
                            v.putInfo("RecessivePartners",
                                    String.format("%s:%s", childName, join("|", indices.toArray(new String[indices.size()]))));
                        }
                    }
                    for (VariantContext v : nonMaternal) {
                        ArrayList<String> indices = new ArrayList<String>(); // TODO
                        // -initial
                        // size
                        for (VariantContext paired_variant : nonPaternal) {
                            indices.add(paired_variant.getInfoValue("Index"));
                        }
                        for (VariantContext paired_variant : deNovo) {
                            indices.add(paired_variant.getInfoValue("Index"));
                        }
                        if (!indices.isEmpty()) {
                            v.addInfo("COMPOUND", childName);
                            v.putInfo("RecessivePartners",
                                    String.format("%s:%s", childName, join("|", indices.toArray(new String[indices.size()]))));
                        }
                    }
                }
            }
        }
    }

    @Override
    public VCFHeader getHeader() {
        return header;
    }

    private static final int[] splitAlleles(String call) {
        // FIXME - assumes GT FORMAT type is present (per VCF spec, if present must be first)
        if (call.startsWith(".")) {
            return new int[]{-1, -1};
        }
        int gtEnd = call.indexOf(':');
        if (gtEnd >= 0) {
            call = call.substring(0, gtEnd);
        }
        int delim = call.indexOf('/');
        if (delim < 0) delim = call.indexOf('|');
        if (delim < 0) return new int[]{-1, -1};
        return new int[]{Integer.parseInt(call.substring(0, delim)), Integer.parseInt(call.substring(delim + 1))};
    }

    private int indexOf(int needle, int[] haystack) {
        for (int i = 0; i < haystack.length; i++) {
            if (needle == haystack[i]) return i;
        }
        return -1;
    }


    @Override
    public boolean hasNext() {
        return filteredVariants.hasNext();
    }

    @Override
    public VariantContext next() {
        return filteredVariants.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        // TODO - do I need to do anything here?
    }

    public static class VCFGeneGrouper extends Grouper<String, VariantContext> {
        @Override
        public String keyForValue(VariantContext v) {
            String gene = v.getInfoValue("Gene_name");
            if (gene == null || gene.isEmpty()) gene = null;
            return gene;
        }
    }

    public static void main(String argv[]) throws IOException {
        BufferedReader br = FileUtils.filenameToBufferedReader(argv[0]);
        VCFParser p = new VCFParser(br);
        VCFHeader headers = p.getHeader();
        headers.addAll(Arrays.asList(ADDITIONAL_HEADERS));
        System.out.print(headers);
        System.out.println(headers.getColumnHeaderLine());

        int yes = 0, no = 0;
        for (CompoundMutationFilter cmf = new CompoundMutationFilter(p, VCFUtils.getTrioIndices(p.getHeader())); cmf.hasNext(); ) {
            VariantContext v = cmf.next();
            if (v.hasInfo("COMPOUND")) {
                yes++;
            } else {
                no++;
            }
            System.out.println(v);
        }
        br.close();
        System.err.println(String.format("%d biallelic mutations,  %d otherwise", yes, no));
    }

    private class PhaseGroup {
        ArrayList<VariantContext> nonPaternal = new ArrayList<VariantContext>();
        ArrayList<VariantContext> nonMaternal = new ArrayList<VariantContext>();
        ArrayList<VariantContext> deNovo = new ArrayList<VariantContext>();
    }

    private static String join(String sep, String... strings) {
        // again?!?
        if (strings.length == 0) return "";
        if (strings.length == 1) return strings[0];
        StringBuilder sb = new StringBuilder();
        for (String s : strings) {
            sb.append(",").append(s);
        }
        return sb.substring(1);
    }

}
