/*
 * aoserv-webservices - SOAP web services for the AOServ Platform.
 * Copyright (C) 2009-2013, 2018, 2020, 2022  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of aoserv-webservices.
 *
 * aoserv-webservices is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * aoserv-webservices is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with aoserv-webservices.  If not, see <https://www.gnu.org/licenses/>.
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
