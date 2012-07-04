addSnpEffSplitter()
addGeneAnnotator("in_omim", "data/omimgenes")
addGeneAnnotator("in_hgmd", "data/hgmdgenes")
addGeneAnnotator("in_cosmic", "data/cosmicgenes")
addGeneAnnotator("exclusion", "data/fajardogenes")
addVCFAnnotator("data/niehs95.indels.vcf.gz", "AF=NIEHSIAF")
addVCFAnnotator("data/niehs95.snps.vcf.gz", "AF=NIEHSAF")
addVCFAnnotator("data/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz",
    "AMR_AF,ASN_AF,AFR_AF,EUR_AF,AF=TGAF")
addVCFAnnotator("data/ncbiSNP135.vcf.gz", 
    "SCS,GMAF,CLN,PM,G5A,G5").setCopyID(true)
addVCFAnnotator("data/ESP6500.vcf.gz",
    "MAF=ESPMAF,GTS,EA_GTC,AA_GTC,FG,GM,AA,AAC,PP,CDP,CG,GS,CA,DP=ESPDP,GL=ESPGL")
addVCFAnnotator("data/Complete_Public_Genomes_54genomes_VQHIGH.vcf.gz",
    "AF=CG54AF").setAddChr(true).setRequirePass(true)
addTSVAnnotator("data/dbNSFP2.0b3_variant.gz",
    "22=SIFT,23=HDIV_SCORE,24=HDIV_PRED,25=HVAR_SCORE,26=HVAR_PRED," +
    "27=LRT_SCORE,28=LRT_PRED,29=MT_SCORE,30=MT_PRED,31=GERP_NR,32=GERP_RS," +
    "33=PHYLOP,36=LRT_OMEGA").checkRef(3).checkAlt(4)
