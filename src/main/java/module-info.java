/*
 * aoserv-webservices - SOAP web services for the AOServ Platform.
 * Copyright (C) 2021, 2022  AO Industries, Inc.
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
module com.aoindustries.aoserv.webservices {
  exports com.aoindustries.aoserv.webservices.v1_0;
  // Direct
  requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.aoapps.net.types; // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
  requires com.aoapps.security; // <groupId>com.aoapps</groupId><artifactId>ao-security</artifactId>
  requires com.aoindustries.aoserv.client; // <groupId>com.aoindustries</groupId><artifactId>aoserv-client</artifactId>
  // Java SE
  requires java.desktop;
  requires java.logging;
  requires java.rmi;
  requires java.sql;
} // TODO: Avoiding rewrite-maven-plugin-4.22.2 truncation
