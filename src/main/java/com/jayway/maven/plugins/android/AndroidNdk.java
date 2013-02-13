/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayway.maven.plugins.android;

import com.jayway.maven.plugins.android.phase05compile.NdkBuildMojo;
import org.apache.commons.lang.SystemUtils;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;

/**
 * Represents an Android NDK.
 *
 * @author Johan Lindquist <johanlindquist@gmail.com>
 */
public class AndroidNdk
{

    public static final String PROPER_NDK_HOME_DIRECTORY_MESSAGE = "Please provide a proper Android NDK directory path"
            + " as configuration parameter <ndk><path>...</path></ndk> in the plugin <configuration/>. As an "
            + "alternative, you may add the parameter to commandline: -Dandroid.ndk.path=... or set environment "
            + "variable " + NdkBuildMojo.ENV_ANDROID_NDK_HOME + ".";

    /**
     * Arm toolchain implementations.
     */
    private static final String[] ARM_TOOLCHAIN = {  "arm-linux-androideabi-4.4.3", "arm-linux-androideabi-4.6",
                                                     "arm-linux-androideabi-4.7" };

    /**
     * x86 toolchain implementations.
     */
    private static final String[] X86_TOOLCHAIN = { "x86-4.4.3", "x86-4.6", "x86-4.7" };

    /**
     * Mips toolchain implementations.
     */
    private static final String[] MIPS_TOOLCHAIN = { "mipsel-linux-android-4.4.3", "mipsel-linux-android-4.6",
                                                     "mipsel-linux-android-4.7" };

    /**
     * Possible locations for the gdbserver file.
     */
    private static final String[] GDB_SERVER_LOCATIONS = { "toolchains/%s/prebuilt/gdbserver",
                                                           "prebuilt/android-%s/gdbserver/gdbserver" };

    private final File ndkPath;

    public AndroidNdk( File ndkPath )
    {
        assertPathIsDirectory( ndkPath );
        this.ndkPath = ndkPath;
    }

    private void assertPathIsDirectory( final File path )
    {
        if ( path == null )
        {
            throw new InvalidNdkException( PROPER_NDK_HOME_DIRECTORY_MESSAGE );
        }
        if ( ! path.isDirectory() )
        {
            throw new InvalidNdkException(
                    "Path \"" + path + "\" is not a directory. " + PROPER_NDK_HOME_DIRECTORY_MESSAGE );
        }
    }

    private File findStripper( String toolchain )
    {
        final File stripper;
        if ( SystemUtils.IS_OS_LINUX )
        {
            return new File( ndkPath,
                             "toolchains/" + toolchain + "/prebuilt/linux-x86/bin/arm-linux-androideabi-strip" );
        }
        else if ( SystemUtils.IS_OS_WINDOWS )
        {
            return new File( ndkPath,
                             "toolchains/" + toolchain + "/prebuilt/windows/bin/arm-linux-androideabi-strip.exe" );
        }
        else if ( SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX )
        {
            return new File( ndkPath,
                             "toolchains/" + toolchain + "/prebuilt/darwin-x86/bin/arm-linux-androideabi-strip" );
        }
        return null;
    }

    public File getStripper( String toolchain ) throws MojoExecutionException
    {
        final File stripper = findStripper( toolchain );
        if ( stripper == null )
        {
            throw new MojoExecutionException( "Could not resolve stripper for current OS: " + SystemUtils.OS_NAME );
        }

        // Some basic validation
        if ( ! stripper.exists() )
        {
            throw new MojoExecutionException( "Strip binary " + stripper.getAbsolutePath()
                    + " does not exist, please double check the toolchain and OS used" );
        }

        // We should be good to go
        return stripper;
    }

    private String resolveNdkToolchain( String[] toolchains )
    {
        for ( String toolchain : toolchains )
        {
            File f = findStripper( toolchain );
            if ( f != null && f.exists() )
            {
                return toolchain;
            }
        }
        return null;
    }

    /**
     * Tries to resolve the toolchain based on the path of the file.
     *
     * @param file Native library
     * @return String
     * @throws MojoExecutionException When no toolchain is found
     */
    public String getToolchain( File file ) throws MojoExecutionException
    {
        String resolvedNdkToolchain = null;

        // try to resolve the toolchain now
        String ndkArchitecture = file.getParentFile().getName();
        if ( "armeabi".equals( ndkArchitecture ) || "armeabi-v7a".equals( ndkArchitecture ) )
        {
            resolvedNdkToolchain = resolveNdkToolchain( ARM_TOOLCHAIN );
        }
        else if ( "x86".equals( ndkArchitecture ) )
        {
            resolvedNdkToolchain = resolveNdkToolchain( X86_TOOLCHAIN );
        }
        else if ( "mips".equals( ndkArchitecture ) )
        {
            resolvedNdkToolchain = resolveNdkToolchain( MIPS_TOOLCHAIN );
        }

        // if no toolchain can be found
        if ( resolvedNdkToolchain == null )
        {
            throw new MojoExecutionException(
                "Can not resolve automatically a toolchain to use. Please specify one." );
        }
        return resolvedNdkToolchain;
    }

    /**
     * Returns the complete path for the ndk-build tool, based on this NDK.
     *
     * @return the complete path as a <code>String</code>, including the tool's filename.
     */
    public String getNdkBuildPath()
    {
        if ( SystemUtils.IS_OS_WINDOWS )
        {
            return new File( ndkPath, "/ndk-build.cmd" ).getAbsolutePath();
        }
        else
        {
            return new File( ndkPath, "/ndk-build" ).getAbsolutePath();
        }
    }

    public File getGdbServer( String toolchain ) throws MojoExecutionException
    {
        // check for the gdb server
        for ( String location : GDB_SERVER_LOCATIONS )
        {
            File gdbServerFile = new File( ndkPath, String.format( location, toolchain ) );
            if ( gdbServerFile.exists() )
            {
                return gdbServerFile;
            }
        }

        //  if we got here, throw an error
        throw new MojoExecutionException( "gdbserver binary for toolchain " + toolchain
            + " does not exist, please double check the toolchain and OS used" );
    }
}