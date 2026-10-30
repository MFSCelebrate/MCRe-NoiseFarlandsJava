/* Copyright (c) 2007-2012 Timothy Wall, All Rights Reserved
 *
 * The contents of this file is dual-licensed under 2
 * alternative Open Source/Free licenses: LGPL 2.1 or later and
 * Apache License 2.0. (starting with JNA version 4.0.0).
 *
 * You can freely decide which license you want to apply to
 * the project.
 *
 * You may obtain a copy of the LGPL License at:
 *
 * http://www.gnu.org/licenses/licenses.html
 *
 * A copy is also included in the downloadable source code package
 * containing JNA, in file "LGPL2.1".
 *
 * You may obtain a copy of the Apache License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code package
 * containing JNA, in file "AL2.0".
 */
package com.sun.jna;
interface Version {
    // Buildsystem replaces variables and compiled class will hold the right value
    // 🔧 MCRe: 源码树自带 JNA 源码（src/main/java/com/sun/jna/），gradle javac 不会执行
    // 官方 ant 构建的"版本注入"步骤 → 占位符 "TEMPLATE" 原样进产物 → 启动时与
    // libjnidispatch.so（自报 7.0.4，官方 5.17.0 配套）版本握手必然失败，
    // 报 "Expected: TEMPLATE Found: 7.0.4" Error（SystemReport 的 memory/software 组挂）。
    // 此处手工补上官方 5.17.0 的注入值：VERSION = JNA Java 版本；VERSION_NATIVE = 配套
    // libjnidispatch.so 自报版本。⚠️ 启动器将来更换 JNA 版本（jna_dir 的 so 自报值变化）需同步更新。
    String VERSION = "5.17.0";
    String VERSION_NATIVE = "7.0.4";
}
