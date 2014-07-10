snpEffSplitter()
geneAnnotator("in_omim", "data/omimgenes")
geneAnnotator("in_hgmd", "data/hgmdgenes")
geneAnnotator("in_cosmic", "data/cosmicgenes")
geneAnnotator("in_genetests", "data/genetestsgenes")
geneAnnotator("exclusion", "data/fajardogenes")
geneAnnotator("hgnclinks", "data/hgnc.txt").hasHeader(true).setKeyColumn(2).
    setOutputColumns("3=HGNC_NAME")
vcfAnnotator("data/niehs95.snps.vcf.gz", "AF=NIEHSAF")
vcfAnnotator("data/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz",
    "AMR_AF,ASN_AF,AFR_AF,EUR_AF,AF=TGAF")
vcfAnnotator("data/ncbiSNP137.vcf.gz", 
    "SCS,GMAF,PM,G5A,G5").setCopyID(true)
vcfAnnotator("data/ESP6500SI-V2.vcf.gz",
    "MAF=ESPMAF,GTS,EA_GTC,AA_GTC,FG,GM,AA,AAC,PP,CDP,CG,GS,CA,DP=ESPDP,GL=ESPGL")
tsvAnnotator("data/dbNSFP2.4_variant.gz",
    "24=SIFT_SCORE,26=SIFT_PRED,27=HDIV_SCORE,29=HDIV_PRED,30=HVAR_SCORE,32=HVAR_PRED," +
    "33=LRT_SCORE,35=LRT_PRED,36=MT_SCORE,38=MT_PRED,39=MA_SCORE,41=MA_PRED," +
    "42=FATHMM_SCORE,44=FATHMM_PRED,54=CADD_PHRED," +
    "55=GERP_NR,56=GERP_RS,58=PHYLOP,73=LRT_OMEGA").useHeader(true).checkRef(3).checkAlt(4)
