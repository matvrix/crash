/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.crsh.lang.groovy.command;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.crsh.cli.impl.descriptor.IntrospectionException;
import org.crsh.command.BaseCommand;
import org.crsh.command.InvocationContext;
import org.crsh.command.NoSuchCommandException;
import org.crsh.command.ScriptException;
import org.crsh.command.ShellCommand;
import org.crsh.shell.impl.command.CRaSH;

public abstract class GroovyCommand extends BaseCommand implements GroovyObject {

  // never persist the MetaClass
  private transient MetaClass metaClass;

  protected GroovyCommand() throws IntrospectionException {
    this.metaClass = InvokerHelper.getMetaClass(this.getClass());
  }

  @Override
  public ScriptException toScript(Throwable cause) {
    return unwrap(cause);
  }

  public static ScriptException unwrap(Throwable cause) {
    if (cause instanceof ScriptException) {
      return (ScriptException)cause;
    } if (cause instanceof groovy.util.ScriptException) {
      // Special handling for groovy.util.ScriptException
      // which may be thrown by scripts because it is imported by default
      // by groovy imports
      String msg = cause.getMessage();
      ScriptException translated;
      if (msg != null) {
        translated = new ScriptException(msg);
      } else {
        translated = new ScriptException();
      }
      translated.setStackTrace(cause.getStackTrace());
      return translated;
    } else {
      return new ScriptException(cause);
    }
  }

  public final Object invokeMethod(String name, Object args) {
    try {
      return getMetaClass().invokeMethod(this, name, args);
    }
    catch (MissingMethodException e) {
      if (context instanceof InvocationContext) {
        CRaSH crash = (CRaSH)context.getSession().get("crash");
        if (crash != null) {
          ShellCommand cmd;
          try {
            cmd = crash.getCommand(name);
          }
          catch (NoSuchCommandException ce) {
            throw new InvokerInvocationException(ce);
          }
          if (cmd != null) {
            // Should we use null instead of "" ?
            return new ClassDispatcher(cmd, this).dispatch("", CommandClosure.unwrapArgs(args));
          }
        }
      }

      //
      Object o = context.getSession().get(name);
      if (o instanceof Closure) {
        Closure closure = (Closure)o;
        if (args instanceof Object[]) {
          Object[] array = (Object[])args;
          if (array.length == 0) {
            return closure.call();
          } else {
            return closure.call(array);
          }
        } else {
          return closure.call(args);
        }
      } else {
        throw e;
      }
    }
  }

  public final Object getProperty(String property) {
    if (context instanceof InvocationContext<?>) {
      CRaSH crash = (CRaSH)context.getSession().get("crash");
      if (crash != null) {
        try {
          ShellCommand cmd = crash.getCommand(property);
          if (cmd != null) {
            return new ClassDispatcher(cmd, this);
          }
        } catch (NoSuchCommandException e) {
          throw new InvokerInvocationException(e);
        }
      }
    }

    //
    try {
      return getMetaClass().getProperty(this, property);
    }
    catch (MissingPropertyException e) {
      return context.getSession().get(property);
    }
  }

  public final void setProperty(String property, Object newValue) {
    try {
      getMetaClass().setProperty(this, property, newValue);
    }
    catch (MissingPropertyException e) {
      context.getSession().put(property, newValue);
    }
  }

  public MetaClass getMetaClass() {
    if (metaClass == null) {
      metaClass = InvokerHelper.getMetaClass(getClass());
    }
    return metaClass;
  }

  public void setMetaClass(MetaClass metaClass) {
    this.metaClass = metaClass;
  }
}
