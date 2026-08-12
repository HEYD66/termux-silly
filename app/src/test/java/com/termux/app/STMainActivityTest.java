package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

public class STMainActivityTest {

    @Test
    public void installRunsInsideRealPtyAndShowsNativeProgress() {
        String script = STMainActivity.getControlScript();

        Assert.assertTrue(script.contains("exec script -q -e -f -a -c"));
        Assert.assertFalse(script.contains("exec > >(tee"));
        Assert.assertTrue(script.contains("apt-get update"));
        Assert.assertTrue(script.contains("apt-get -y -o Dpkg::Options::=--force-confold upgrade"));
        Assert.assertTrue(script.contains("git clone --progress"));
        Assert.assertTrue(script.contains("npm install --omit=dev"));
        Assert.assertTrue(script.contains("--progress=true --loglevel=info"));

        int runtime = script.indexOf("install_runtime_packages");
        int source = script.indexOf("正在下载或检查 SillyTavern 源码");
        int nodeModules = script.indexOf("正在安装 Node.js 依赖");
        Assert.assertTrue(runtime >= 0 && source > runtime && nodeModules > source);
    }
}
