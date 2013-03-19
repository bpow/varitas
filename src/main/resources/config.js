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
vcfAnnotator("data/ESP6500SI.vcf.gz",
    "MAF=ESPMAF,GTS,EA_GTC,AA_GTC,FG,GM,AA,AAC,PP,CDP,CG,GS,CA,DP=ESPDP,GL=ESPGL")
vcfAnnotator("data/Complete_Public_Genomes_54genomes_VQHIGH.vcf.gz",
    "AF=CG54AF").setAddChr(true).setRequirePass(true)
tsvAnnotator("data/dbNSFP2.0_variant.gz",
    "22=SIFT,23=HDIV_SCORE,24=HDIV_PRED,25=HVAR_SCORE,26=HVAR_PRED," +
    "27=LRT_SCORE,28=LRT_PRED,29=MT_SCORE,30=MT_PRED,31=MA_SCORE,32=MA_PRED," +
    "33=FATHMM_SCORE,34=GERP_NR,35=GERP_RS,36=PHYLOP,39=LRT_OMEGA").useHeader(true).checkRef(3).checkAlt(4)
