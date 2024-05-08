package com.idlogix.pfc.promotion.models;

import java.math.BigDecimal;

public class Promotion {
	
	int idl_promotionline_id;
	int c_carge_id;
	int m_product_id;
	BigDecimal amount;
	BigDecimal minqty;
	public int getIdl_promotionline_id() {
		return idl_promotionline_id;
	}
	public void setIdl_promotionline_id(int idl_promotionline_id) {
		this.idl_promotionline_id = idl_promotionline_id;
	}
	public int getC_carge_id() {
		return c_carge_id;
	}
	public void setC_carge_id(int c_carge_id) {
		this.c_carge_id = c_carge_id;
	}
	public int getM_product_id() {
		return m_product_id;
	}
	public void setM_product_id(int m_product_id) {
		this.m_product_id = m_product_id;
	}
	public BigDecimal getAmount() {
		return amount;
	}
	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}
	public BigDecimal getMinqty() {
		return minqty;
	}
	public void setMinqty(BigDecimal minqty) {
		this.minqty = minqty;
	}
	
	

}
