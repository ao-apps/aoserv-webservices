/*
 * Copyright 2009-2013 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.aoserv.webservices.v1_0;

import com.aoindustries.aoserv.client.dto.UserId;

/**
 * @author  AO Industries, Inc.
 */
public class Credentials {

    private String locale;
    private UserId username;
    private String password;
    private UserId switchUser;

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public UserId getUsername() {
        return username;
    }

    public void setUsername(UserId username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserId getSwitchUser() {
        return switchUser;
    }

    public void setSwitchUser(UserId switchUser) {
        this.switchUser = switchUser;
    }
}
