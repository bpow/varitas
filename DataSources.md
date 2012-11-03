Some useful data sources
========================

Thousand genomes whole-genome calls
-----------------------------------

URL: ftp://ftp-trace.ncbi.nih.gov/1000genomes/ftp/release/20110521/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz

Index: ftp://ftp-trace.ncbi.nih.gov/1000genomes/ftp/release/20110521/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz.tbi

README: ftp://ftp-trace.ncbi.nih.gov/1000genomes/ftp/release/20110521/README.phase1_integrated_release_version3_20120430

Info: www.1000genomes.org

Cite: http://www.nature.com/nature/journal/v467/n7319/full/nature09534.html

TODO: look into their functional calls

License: http://www.1000genomes.org/category/data-reuse

LastUpdate: 2012-04-26


NCBI dbSNP
----------

URL: ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/00-All.vcf.gz

Index: ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/00-All.vcf.gz.tbi

README: ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/human_9606/VCF/00--README.txt

Info: http://www.ncbi.nlm.nih.gov/projects/SNP/

Cite: Database of Single Nucleotide Polymorphisms (dbSNP). Bethesda (MD): National Center for Biotechnology Information, National Library of Medicine. (dbSNP Build ID: {build ID}).
Available from: http://www.ncbi.nlm.nih.gov/SNP/

LastUpdate: 2012-06 (137)


Complete Genomics 54 genomes
----------------------------

URL: ftp://ftp2.completegenomics.com/Multigenome_summaries/Complete_Public_Genomes_54genomes_B37_mkvcf.vcf.bz2

PostProcess: bzcat, bgzip, index

TODO: filter for VQHIGH?

README: ftp://ftp2.completegenomics.com/Multigenome_summaries/Complete_Genomics_Multigenome_summaries_README.pdf

Info: ftp://ftp2.completegenomics.com/Public_Genomes_Dataset_Service_Note.pdf

License:
> The data are freely available for use in a publication with
> the following stipulations:
> 1. The Coriell and ATCC Repository number(s) of the
>    cell line(s) or the DNA sample(s) must be cited in
>    publication or presentations that are based on the
>    use of these materials.
> 2. Our Science paper (R. Drmanac, et. al. Science
>    327(5961), 78. [DOI: 10.1126/science.1181498])
>    must be referenced.
> 3. The version number of the Complete Genomics
>    assembly software with which the data was
>    generated must be cited. This can be found in the
>    header of the summary.tsv file (# Software_Version).


ESP6500 (NIEHS GO ESP)
----------------------

URL: http://evs.gs.washington.edu/evs_bulk_data/ESP6500.snps.vcf.tar.gz

PostProcess: untgz, combine, bgzip, index

License:
> "We request that any use of data obtained from the NHLBI GO ESP Exome
> Variant Server be cited in publications."

Cite: Exome Variant Server, NHLBI GO Exome Sequencing Project (ESP), Seattle, WA (URL: http://evs.gs.washington.edu/EVS/) [date (month, yr) accessed].

Info: http://evs.gs.washington.edu/EVS/


NIEHS Environmental Genome Project
----------------------------------

Note: There is no download url, the files must be downloaded using the aspera client

PostProcess: gunzip, bgzip, index (combine snps/indels?)

License:
> "We request that any use of data obtained from the NIEHS Exome Variant
> Server be cited in publications."

Cite: NIEHS Environmental Genome Project, Seattle, WA (URL: http://evs.gs.washington.edu/niehsExome/) [date (month, yr) accessed].

Info: http://evs.gs.washington.edu/niehsExome/


dbNSFP
------

URL: http://dbnsfp.houstonbioinformatics.org/dbNSFPzip/dbNSFP2.0b3.zip

PostProcess: unzip, combine, bgzip, index

README: http://dbnsfp.houstonbioinformatics.org/dbNSFPzip/dbNSFP2.0b3.readme.txt

Info: https://sites.google.com/site/jpopgen/dbNSFP

Cite: Liu X, Jian X, and Boerwinkle E. 2011. dbNSFP: a lightweight database of human non-synonymous SNPs and their functional predictions. Human Mutation. 32:894-899.




