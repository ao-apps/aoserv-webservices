/*
 * Copyright 2009-2013, 2017, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.aoserv.webservices.v1_0;

import com.aoindustries.aoserv.client.dto.UserName;

/**
 * @author  AO Industries, Inc.
 */
public class Credentials {

	private String locale;
	private UserName username;
	private String password;
	private UserName switchUser;

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public UserName getUsername() {
		return username;
	}

	public void setUsername(UserName username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public UserName getSwitchUser() {
		return switchUser;
	}

	public void setSwitchUser(UserName switchUser) {
		this.switchUser = switchUser;
	}
}
