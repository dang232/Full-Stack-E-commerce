package com.vnshop.invoiceservice.application.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * NMHDon — buyer information on the invoice.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class NMHDon {

    /** Buyer name */
    @XmlElement(name = "Ten")
    private String ten;

    /** Buyer tax code (B2B only; omitted for B2C) */
    @XmlElement(name = "MST")
    private String mst;

    /** Buyer address */
    @XmlElement(name = "DChi")
    private String dchi;

    /** Buyer email */
    @XmlElement(name = "DCTDTu")
    private String dctdtu;

    public NMHDon() {}

    public String getTen() { return ten; }
    public void setTen(String ten) { this.ten = ten; }

    public String getMst() { return mst; }
    public void setMst(String mst) { this.mst = mst; }

    public String getDchi() { return dchi; }
    public void setDchi(String dchi) { this.dchi = dchi; }

    public String getDctdtu() { return dctdtu; }
    public void setDctdtu(String dctdtu) { this.dctdtu = dctdtu; }
}
