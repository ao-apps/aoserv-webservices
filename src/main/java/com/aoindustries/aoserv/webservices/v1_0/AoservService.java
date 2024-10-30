/*
 * aoserv-webservices - SOAP web services for the AOServ Platform.
 * Copyright (C) 2009-2013, 2017, 2018, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import com.aoapps.hodgepodge.ws.WsEncoder;
import com.aoapps.lang.NullArgumentException;
import com.aoapps.lang.dto.DtoFactory;
import com.aoapps.lang.i18n.Locales;
import com.aoapps.lang.i18n.ThreadLocale;
import com.aoapps.lang.validation.ValidationResult;
import com.aoapps.net.Protocol;
import com.aoapps.net.dto.DomainLabel;
import com.aoapps.net.dto.DomainLabels;
import com.aoapps.net.dto.DomainName;
import com.aoapps.net.dto.Email;
import com.aoapps.net.dto.HostAddress;
import com.aoapps.net.dto.InetAddress;
import com.aoapps.net.dto.MacAddress;
import com.aoapps.net.dto.Port;
import com.aoapps.security.Password;
import com.aoindustries.aoserv.client.AoservConnector;
import com.aoindustries.aoserv.client.dto.AccountName;
import com.aoindustries.aoserv.client.dto.Gecos;
import com.aoindustries.aoserv.client.dto.HashedKey;
import com.aoindustries.aoserv.client.dto.HashedPassword;
import com.aoindustries.aoserv.client.dto.LinuxDaemonAcl;
import com.aoindustries.aoserv.client.dto.LinuxGroupName;
import com.aoindustries.aoserv.client.dto.LinuxId;
import com.aoindustries.aoserv.client.dto.LinuxServer;
import com.aoindustries.aoserv.client.dto.LinuxUserName;
import com.aoindustries.aoserv.client.dto.MysqlDatabaseName;
import com.aoindustries.aoserv.client.dto.MysqlServerName;
import com.aoindustries.aoserv.client.dto.MysqlTableName;
import com.aoindustries.aoserv.client.dto.MysqlUserName;
import com.aoindustries.aoserv.client.dto.PosixPath;
import com.aoindustries.aoserv.client.dto.PostgresDatabaseName;
import com.aoindustries.aoserv.client.dto.PostgresServerName;
import com.aoindustries.aoserv.client.dto.PostgresUserName;
import com.aoindustries.aoserv.client.dto.UserName;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.login.AccountLockedException;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

/**
 * Exposes the connector configured in aoserv-client.properties as a web service.
 * <ol>
 * <li>Update source: Clean/Build aoserv-webservices</li>
 * <li>Deploy: Clean/Run aoserv-webservices-webapp</li>
 * <li>Rebuild client (see wsdl2java)</li>
 * </ol>
 *
 * <p>All primitives are nillable in generated WSDL: http://issues.apache.org/jira/browse/AXIS2-4542
 * It works, but is less efficient due to everything being wrapped in client side.</p>
 */
public class AoservService {

  private static final Logger logger = Logger.getLogger(AoservService.class.getName());

  // <editor-fold defaultstate="collapsed" desc="Exception conversion">
  private static RemoteException toRemoteException(Throwable t) {
    logger.log(Level.SEVERE, null, t);
    if (t.getClass() == RemoteException.class && t.getCause() == null) {
      return (RemoteException) t;
    }
    return new RemoteException(t.getLocalizedMessage());
  }

  private static LoginException toLoginException(Throwable t) {
    logger.log(Level.SEVERE, null, t);
    if (t.getClass() == LoginException.class && t.getCause() == null) {
      return (LoginException) t;
    }
    return new LoginException(t.getLocalizedMessage());
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Static Utilities">
  private static String nullIfEmpty(String value) {
    if (value == null || value.length() == 0) {
      return null;
    }
    return value;
  }

  private static Locale getLocale(Credentials credentials) {
    String localeName = nullIfEmpty(credentials.getLocale());
    return localeName == null ? Locale.getDefault() : Locales.parseLocale(localeName);
  }

  /**
   * Connectors are cached so the LoginException "ping" check is only called the first time.
   * This is to avoid an unnecessary round-trip to the server for each call.
   */
  static class ConnectorCacheKey {

    private final com.aoindustries.aoserv.client.account.User.Name username;
    private final String password; // TODO: Store as Password object or char[], destroy when evicting from cache or shutting down service, length-constant comparisons
    private final com.aoindustries.aoserv.client.account.User.Name switchUser;
    private final int hash;

    ConnectorCacheKey(com.aoindustries.aoserv.client.account.User.Name username, String password, com.aoindustries.aoserv.client.account.User.Name switchUser) {
      this.username = NullArgumentException.checkNotNull(username, "username");
      this.password = NullArgumentException.checkNotNull(password, "password");
      this.switchUser = NullArgumentException.checkNotNull(switchUser, "switchUser");
      int newHash = username.hashCode();
      newHash = newHash * 31 + password.hashCode();
      newHash = newHash * 31 + switchUser.hashCode();
      this.hash = newHash;
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ConnectorCacheKey)) {
        return false;
      }
      ConnectorCacheKey other = (ConnectorCacheKey) obj;
      return
          // hash check shortcut
          hash == other.hash // TODO: No shortcut for length-constant time?
              // .equals fields
              && username.equals(other.username)
              && password.equals(other.password)
              && switchUser.equals(other.switchUser);
    }
  }

  /**
   * Cache of connectors.
   */
  private static final ConcurrentMap<ConnectorCacheKey, AoservConnector> connectorCache = new ConcurrentHashMap<>();

  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  private static AoservConnector getConnector(Credentials credentials) throws LoginException, RemoteException {
    try {
      com.aoindustries.aoserv.client.account.User.Name username = com.aoindustries.aoserv.client.account.User.Name.valueOf(credentials.getUsername().getName());
      String password = credentials.getPassword();
      com.aoindustries.aoserv.client.account.User.Name switchUser = com.aoindustries.aoserv.client.account.User.Name.valueOf(
          credentials.getSwitchUser() == null
              ? null
              : nullIfEmpty(credentials.getSwitchUser().getName())
      );
      if (switchUser == null) {
        switchUser = username;
      }
      ConnectorCacheKey cacheKey = new ConnectorCacheKey(username, password, switchUser);
      // Check cache first
      AoservConnector conn = connectorCache.get(cacheKey);
      if (conn == null) {
        try {
          conn = AoservConnector.getConnector(
              switchUser,
              username,
              password,
              null
          );
          conn.ping();
          AoservConnector existing = connectorCache.putIfAbsent(cacheKey, conn);
          if (existing != null) {
            conn = existing;
          }
        } catch (ThreadDeath td) {
          throw td;
        } catch (IOException err) {
          String message = err.getMessage();
          if (message != null) {
            if (message.contains("Unable to find BusinessAdministrator")) {
              throw toLoginException(new AccountNotFoundException("Account Not Found"));
            }
            if (message.contains("Connection attempted with invalid password")) {
              throw toLoginException(new FailedLoginException("Incorrect Password"));
            }
            if (message.contains("BusinessAdministrator disabled")) {
              throw toLoginException(new AccountLockedException("Account Disabled"));
            }
          }
          throw toRemoteException(err);
        } catch (Throwable t) {
          throw toRemoteException(t);
        }
      }
      return conn;
    } catch (ThreadDeath | LoginException | RemoteException td) {
      throw td;
    } catch (Throwable t) {
      throw toRemoteException(t);
    }
  }

  private static final ConcurrentMap<Class<?>, PropertyDescriptor[]> stringProperties = new ConcurrentHashMap<>();

  private static PropertyDescriptor[] getStringProperties(Class<?> type) throws IntrospectionException {
    PropertyDescriptor[] props = stringProperties.get(type);
    if (props == null) {
      PropertyDescriptor[] allProps = Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors();
      List<PropertyDescriptor> newStringProps = new ArrayList<>(allProps.length);
      for (PropertyDescriptor property : allProps) {
        if (property.getPropertyType() == String.class) {
          newStringProps.add(property);
        }
      }
      props = newStringProps.toArray(new PropertyDescriptor[newStringProps.size()]);
      stringProperties.put(type, props);
    }
    return props;
  }

  /*private static void checkDtoString(PropertyDescriptor property, String value) throws RemoteException {
    if (value != null) {
      int len = value.length();
      for (int i=0;i<len;i++) {
        char ch = value.charAt(i);
        if (ch<' ' && ch != '\n' && ch != '\r') {
          throw new RemoteException("Invalid character in property "+property.getName()+": "+Integer.toString(ch, 16)+": "+value);
        }
      }
    }
  }*/

  /**
   * Converts the collection to an array of data transfer objects in arbitrary order.
   */
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch", "StringEquality"})
  static <T> T[] getDtoArray(Class<T> clazz, Collection<? extends DtoFactory<? extends T>> set) throws RemoteException {
    try {
      int size = set.size();
      @SuppressWarnings("unchecked")
      T[] array = (T[]) Array.newInstance(clazz, size);

      PropertyDescriptor[] stringProps = getStringProperties(clazz);
      int index = 0;
      for (DtoFactory<? extends T> dtoFactory : set) {
        T dto = dtoFactory.getDto();

        // Encode string properties to avoid invalid characters
        for (PropertyDescriptor property : stringProps) {
          String value = (String) property.getReadMethod().invoke(dto);
          String encoded = WsEncoder.encode(value);
          if (
              // String identity equals intentional:
              encoded != value
          ) {
            //System.out.println("WsEncoded: "+dtoFactory.getClass().getName()+": "+dtoFactory);
            property.getWriteMethod().invoke(dto, encoded);
          }
        }
        array[index++] = dto;
      }
      if (index != size) {
        throw new AssertionError("index != size: " + index + " != " + size);
      }
      return array;
    } catch (ThreadDeath td) {
      throw td;
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      throw toRemoteException(cause == null ? e : cause);
    } catch (Throwable t) {
      throw toRemoteException(t);
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Validation">
  public String validateAccountName(Credentials credentials, AccountName accounting) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.account.Account.Name.validate(accounting.getAccounting());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateDomainLabel(Credentials credentials, DomainLabel label) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoapps.net.DomainLabel.validate(label.getLabel());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateDomainLabels(Credentials credentials, DomainLabels labels) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoapps.net.DomainLabels.validate(labels.getLabels());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateDomainName(Credentials credentials, DomainName domain) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoapps.net.DomainName.validate(domain.getDomain());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateEmail(Credentials credentials, Email email) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoapps.net.Email.validate(email.getLocalPart(), email.getDomain().getDomain());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateGecos(Credentials credentials, Gecos gecos) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.linux.User.Gecos.validate(gecos.getValue());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateGroupName(Credentials credentials, LinuxGroupName groupName) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.linux.Group.Name.validate(groupName.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateHashedKey(Credentials credentials, HashedKey hashedKey) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      try {
        com.aoapps.security.HashedKey.valueOf(hashedKey.getHashedKey());
        return null;
      } catch (IllegalArgumentException e) {
        String message = e.getLocalizedMessage();
        if (message == null || message.isEmpty()) {
          message = e.getMessage();
        }
        if (message == null || message.isEmpty()) {
          message = e.toString();
        }
        return message;
      }
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateHashedPassword(Credentials credentials, HashedPassword hashedPassword) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      try {
        com.aoapps.security.HashedPassword.valueOf(hashedPassword.getHashedPassword());
        return null;
      } catch (IllegalArgumentException e) {
        String message = e.getLocalizedMessage();
        if (message == null || message.isEmpty()) {
          message = e.getMessage();
        }
        if (message == null || message.isEmpty()) {
          message = e.toString();
        }
        return message;
      }
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateHostname(Credentials credentials, HostAddress hostname) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoapps.net.HostAddress.validate(hostname.getAddress());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateInetAddress(Credentials credentials, InetAddress ip) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoapps.net.InetAddress.validate(ip.getAddress());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateLinuxId(Credentials credentials, LinuxId linuxId) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.linux.LinuxId.validate(linuxId.getId());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateLinuxUserName(Credentials credentials, LinuxUserName name) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.linux.User.Name.validate(name.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateMacAddress(Credentials credentials, MacAddress address) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoapps.net.MacAddress.validate(address.getAddress());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateMysqlDatabaseName(Credentials credentials, MysqlDatabaseName name) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.mysql.Database.Name.validate(name.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateMysqlServerName(Credentials credentials, MysqlServerName name) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.mysql.Server.Name.validate(name.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateMysqlTableName(Credentials credentials, MysqlTableName name) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.mysql.TableName.validate(name.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateMysqlUserName(Credentials credentials, MysqlUserName name) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.mysql.User.Name.validate(name.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validatePort(Credentials credentials, Port port) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoapps.net.Port.validate(
          port.getPort(),
          Protocol.valueOf(port.getProtocol())
      );
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validatePostgresDatabaseName(Credentials credentials, PostgresDatabaseName name) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.postgresql.Database.Name.validate(name.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validatePostgresServerName(Credentials credentials, PostgresServerName name) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.postgresql.Server.Name.validate(name.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validatePostgresUserName(Credentials credentials, PostgresUserName userName) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.postgresql.User.Name.validate(userName.getName());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validatePosixPath(Credentials credentials, PosixPath posixPath) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.linux.PosixPath.validate(posixPath.getPath());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  public String validateUserName(Credentials credentials, UserName name) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials); // Checks authentication
      ValidationResult result = com.aoindustries.aoserv.client.account.User.Name.validate(name.toString());
      return result.isValid() ? null : result.toString();
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Passwords">
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  public boolean passwordMatches(Credentials credentials, HashedPassword hashedPassword, String plaintext) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      AoservConnector conn = getConnector(credentials);
      return com.aoapps.security.HashedPassword.valueOf(hashedPassword.getHashedPassword()).matches(new Password(plaintext.toCharArray()));
    } catch (ThreadDeath | LoginException e) {
      throw e;
    } catch (Throwable t) {
      throw toRemoteException(t);
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Commands">
  // TODO
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Tables">
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  public LinuxDaemonAcl[] getLinuxDaemonAcl(Credentials credentials) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      return getDtoArray(LinuxDaemonAcl.class, getConnector(credentials).getLinux().getDaemonAcl().getRows());
    } catch (ThreadDeath | LoginException | RemoteException e) {
      throw e;
    } catch (Throwable t) {
      throw toRemoteException(t);
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }

  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  public LinuxServer[] getLinuxServer(Credentials credentials) throws LoginException, RemoteException {
    Locale oldLocale = ThreadLocale.get();
    try {
      ThreadLocale.set(getLocale(credentials));
      return getDtoArray(LinuxServer.class, getConnector(credentials).getLinux().getServer().getRows());
    } catch (ThreadDeath | LoginException | RemoteException e) {
      throw e;
    } catch (Throwable t) {
      throw toRemoteException(t);
    } finally {
      ThreadLocale.set(oldLocale);
    }
  }
  // </editor-fold>
}
