package com.ikverse.egxanalyzer.ui

import androidx.annotation.DrawableRes
import com.ikverse.egxanalyzer.R

/**
 * Which bundled logo belongs to which ticker.
 *
 * Generated, and deliberately a `when` over string literals rather than a lookup by resource name.
 * `Resources.getIdentifier` would have been three lines instead of 222, but it reaches the
 * drawables by a string R8 cannot see - so a release build strips every one of them as unused and
 * the app ships 222 missing logos with nothing failing at compile time to say so. Naming each
 * `R.drawable` here is what keeps them in the APK.
 *
 * Covers the 222 EGX companies that have a logo. `EgxCatalog.refresh` pulls its list from a
 * remote endpoint, so a ticker can arrive that is not here at all - hence the null, and the
 * monogram [StockLogo] draws in its place.
 */
internal object StockLogos {
    /** The drawable for [ticker], or null where no logo is bundled for it. */
    @DrawableRes
    fun forTicker(ticker: String): Int? = when (ticker.trim().uppercase().removeSuffix(".CA")) {
        "AALR" -> R.drawable.logo_aalr
        "ABUK" -> R.drawable.logo_abuk
        "ACAMD" -> R.drawable.logo_acamd
        "ACAP" -> R.drawable.logo_acap
        "ACGC" -> R.drawable.logo_acgc
        "ACTF" -> R.drawable.logo_actf
        "ADCI" -> R.drawable.logo_adci
        "ADIB" -> R.drawable.logo_adib
        "ADPC" -> R.drawable.logo_adpc
        "AFDI" -> R.drawable.logo_afdi
        "AFMC" -> R.drawable.logo_afmc
        "AIDC" -> R.drawable.logo_aidc
        "AIHC" -> R.drawable.logo_aihc
        "AJWA" -> R.drawable.logo_ajwa
        "ALCN" -> R.drawable.logo_alcn
        "ALRA" -> R.drawable.logo_alra
        "ALUM" -> R.drawable.logo_alum
        "AMER" -> R.drawable.logo_amer
        "AMES" -> R.drawable.logo_ames
        "AMIA" -> R.drawable.logo_amia
        "AMOC" -> R.drawable.logo_amoc
        "ANFI" -> R.drawable.logo_anfi
        "APSW" -> R.drawable.logo_apsw
        "ARAB" -> R.drawable.logo_arab
        "ARCC" -> R.drawable.logo_arcc
        "AREH" -> R.drawable.logo_areh
        "ASCM" -> R.drawable.logo_ascm
        "ASPI" -> R.drawable.logo_aspi
        "ATLC" -> R.drawable.logo_atlc
        "ATQA" -> R.drawable.logo_atqa
        "AXPH" -> R.drawable.logo_axph
        "BINV" -> R.drawable.logo_binv
        "BIOC" -> R.drawable.logo_bioc
        "BONY" -> R.drawable.logo_bony
        "BTFH" -> R.drawable.logo_btfh
        "CAED" -> R.drawable.logo_caed
        "CANA" -> R.drawable.logo_cana
        "CCAP" -> R.drawable.logo_ccap
        "CCRS" -> R.drawable.logo_ccrs
        "CEFM" -> R.drawable.logo_cefm
        "CERA" -> R.drawable.logo_cera
        "CFGH" -> R.drawable.logo_cfgh
        "CICH" -> R.drawable.logo_cich
        "CIEB" -> R.drawable.logo_cieb
        "CIRA" -> R.drawable.logo_cira
        "CLHO" -> R.drawable.logo_clho
        "CNFN" -> R.drawable.logo_cnfn
        "COMI" -> R.drawable.logo_comi
        "COPR" -> R.drawable.logo_copr
        "COSG" -> R.drawable.logo_cosg
        "CPCI" -> R.drawable.logo_cpci
        "CPME" -> R.drawable.logo_cpme
        "CRST" -> R.drawable.logo_crst
        "CSAG" -> R.drawable.logo_csag
        "DAPH" -> R.drawable.logo_daph
        "DCCC" -> R.drawable.logo_dccc
        "DEIN" -> R.drawable.logo_dein
        "DGTZ" -> R.drawable.logo_dgtz
        "DOMT" -> R.drawable.logo_domt
        "DSCW" -> R.drawable.logo_dscw
        "DTPP" -> R.drawable.logo_dtpp
        "EALR" -> R.drawable.logo_ealr
        "EASB" -> R.drawable.logo_easb
        "EAST" -> R.drawable.logo_east
        "EBSC" -> R.drawable.logo_ebsc
        "ECAP" -> R.drawable.logo_ecap
        "EDFM" -> R.drawable.logo_edfm
        "EEII" -> R.drawable.logo_eeii
        "EFIC" -> R.drawable.logo_efic
        "EFID" -> R.drawable.logo_efid
        "EFIH" -> R.drawable.logo_efih
        "EGAL" -> R.drawable.logo_egal
        "EGAS" -> R.drawable.logo_egas
        "EGBE" -> R.drawable.logo_egbe
        "EGCH" -> R.drawable.logo_egch
        "EGSA" -> R.drawable.logo_egsa
        "EGTS" -> R.drawable.logo_egts
        "EHDR" -> R.drawable.logo_ehdr
        "ELEC" -> R.drawable.logo_elec
        "ELKA" -> R.drawable.logo_elka
        "ELNA" -> R.drawable.logo_elna
        "ELSH" -> R.drawable.logo_elsh
        "ELWA" -> R.drawable.logo_elwa
        "EMFD" -> R.drawable.logo_emfd
        "ENGC" -> R.drawable.logo_engc
        "EOSB" -> R.drawable.logo_eosb
        "EPCO" -> R.drawable.logo_epco
        "EPPK" -> R.drawable.logo_eppk
        "ETEL" -> R.drawable.logo_etel
        "ETRS" -> R.drawable.logo_etrs
        "EXPA" -> R.drawable.logo_expa
        "FAIT" -> R.drawable.logo_fait
        "FAITA" -> R.drawable.logo_faita
        "FERC" -> R.drawable.logo_ferc
        "FWRY" -> R.drawable.logo_fwry
        "GBCO" -> R.drawable.logo_gbco
        "GDWA" -> R.drawable.logo_gdwa
        "GGCC" -> R.drawable.logo_ggcc
        "GGRN" -> R.drawable.logo_ggrn
        "GIHD" -> R.drawable.logo_gihd
        "GMCI" -> R.drawable.logo_gmci
        "GOUR" -> R.drawable.logo_gour
        "GPIM" -> R.drawable.logo_gpim
        "GPPL" -> R.drawable.logo_gppl
        "GRCA" -> R.drawable.logo_grca
        "GSSC" -> R.drawable.logo_gssc
        "GTEX" -> R.drawable.logo_gtex
        "GTWL" -> R.drawable.logo_gtwl
        "HDBK" -> R.drawable.logo_hdbk
        "HELI" -> R.drawable.logo_heli
        "HRHO" -> R.drawable.logo_hrho
        "ICID" -> R.drawable.logo_icid
        "ICLE" -> R.drawable.logo_icle
        "IDRE" -> R.drawable.logo_idre
        "IFAP" -> R.drawable.logo_ifap
        "INFI" -> R.drawable.logo_infi
        "IRON" -> R.drawable.logo_iron
        "ISMA" -> R.drawable.logo_isma
        "ISMQ" -> R.drawable.logo_ismq
        "ISPH" -> R.drawable.logo_isph
        "JUFO" -> R.drawable.logo_jufo
        "KABO" -> R.drawable.logo_kabo
        "KRDI" -> R.drawable.logo_krdi
        "KWIN" -> R.drawable.logo_kwin
        "KZPC" -> R.drawable.logo_kzpc
        "LCSW" -> R.drawable.logo_lcsw
        "LUTS" -> R.drawable.logo_luts
        "MAAL" -> R.drawable.logo_maal
        "MASR" -> R.drawable.logo_masr
        "MBSC" -> R.drawable.logo_mbsc
        "MCQE" -> R.drawable.logo_mcqe
        "MCRO" -> R.drawable.logo_mcro
        "MEGM" -> R.drawable.logo_megm
        "MENA" -> R.drawable.logo_mena
        "MEPA" -> R.drawable.logo_mepa
        "MFPC" -> R.drawable.logo_mfpc
        "MFSC" -> R.drawable.logo_mfsc
        "MHOT" -> R.drawable.logo_mhot
        "MICH" -> R.drawable.logo_mich
        "MILS" -> R.drawable.logo_mils
        "MIPH" -> R.drawable.logo_miph
        "MMAT" -> R.drawable.logo_mmat
        "MOED" -> R.drawable.logo_moed
        "MOIL" -> R.drawable.logo_moil
        "MOIN" -> R.drawable.logo_moin
        "MOSC" -> R.drawable.logo_mosc
        "MPCI" -> R.drawable.logo_mpci
        "MPCO" -> R.drawable.logo_mpco
        "MPRC" -> R.drawable.logo_mprc
        "MTIE" -> R.drawable.logo_mtie
        "NAHO" -> R.drawable.logo_naho
        "NAPR" -> R.drawable.logo_napr
        "NARE" -> R.drawable.logo_nare
        "NCCW" -> R.drawable.logo_nccw
        "NDRL" -> R.drawable.logo_ndrl
        "NEDA" -> R.drawable.logo_neda
        "NHPS" -> R.drawable.logo_nhps
        "NINH" -> R.drawable.logo_ninh
        "NIPH" -> R.drawable.logo_niph
        "OBRI" -> R.drawable.logo_obri
        "OCDI" -> R.drawable.logo_ocdi
        "OCPH" -> R.drawable.logo_ocph
        "ODIN" -> R.drawable.logo_odin
        "OFH" -> R.drawable.logo_ofh
        "OIH" -> R.drawable.logo_oih
        "OLFI" -> R.drawable.logo_olfi
        "ORAS" -> R.drawable.logo_oras
        "ORHD" -> R.drawable.logo_orhd
        "ORWE" -> R.drawable.logo_orwe
        "PHAR" -> R.drawable.logo_phar
        "PHDC" -> R.drawable.logo_phdc
        "PHGC" -> R.drawable.logo_phgc
        "PHTV" -> R.drawable.logo_phtv
        "POCO" -> R.drawable.logo_poco
        "POUL" -> R.drawable.logo_poul
        "PRCL" -> R.drawable.logo_prcl
        "PRDC" -> R.drawable.logo_prdc
        "PRMH" -> R.drawable.logo_prmh
        "QNBE" -> R.drawable.logo_qnbe
        "RACC" -> R.drawable.logo_racc
        "RAKT" -> R.drawable.logo_rakt
        "RAYA" -> R.drawable.logo_raya
        "RMDA" -> R.drawable.logo_rmda
        "ROTO" -> R.drawable.logo_roto
        "RREI" -> R.drawable.logo_rrei
        "RTVC" -> R.drawable.logo_rtvc
        "RUBX" -> R.drawable.logo_rubx
        "SAIB" -> R.drawable.logo_saib
        "SAUD" -> R.drawable.logo_saud
        "SCEM" -> R.drawable.logo_scem
        "SCFM" -> R.drawable.logo_scfm
        "SCTS" -> R.drawable.logo_scts
        "SDTI" -> R.drawable.logo_sdti
        "SEIG" -> R.drawable.logo_seig
        "SEIGA" -> R.drawable.logo_seiga
        "SIPC" -> R.drawable.logo_sipc
        "SKPC" -> R.drawable.logo_skpc
        "SMFR" -> R.drawable.logo_smfr
        "SNFC" -> R.drawable.logo_snfc
        "SPHT" -> R.drawable.logo_spht
        "SPIN" -> R.drawable.logo_spin
        "SPMD" -> R.drawable.logo_spmd
        "SUGR" -> R.drawable.logo_sugr
        "SVCE" -> R.drawable.logo_svce
        "SWDY" -> R.drawable.logo_swdy
        "TALM" -> R.drawable.logo_talm
        "TANM" -> R.drawable.logo_tanm
        "TAQA" -> R.drawable.logo_taqa
        "TMGH" -> R.drawable.logo_tmgh
        "TRTO" -> R.drawable.logo_trto
        "UBEE" -> R.drawable.logo_ubee
        "UEFM" -> R.drawable.logo_uefm
        "UEGC" -> R.drawable.logo_uegc
        "UNIP" -> R.drawable.logo_unip
        "UNIT" -> R.drawable.logo_unit
        "VALU" -> R.drawable.logo_valu
        "VLMR" -> R.drawable.logo_vlmr
        "VLMRA" -> R.drawable.logo_vlmra
        "WCDF" -> R.drawable.logo_wcdf
        "WKOL" -> R.drawable.logo_wkol
        "ZEOT" -> R.drawable.logo_zeot
        "ZMID" -> R.drawable.logo_zmid
        else -> null
    }

    /** How many logos are bundled. Lets a test notice the set shrinking without listing them all. */
    const val COUNT: Int = 222
}
