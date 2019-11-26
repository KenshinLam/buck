/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.features.apple.projectV2;

import static com.facebook.buck.apple.AppleBundleDescription.WATCH_OS_FLAVOR;
import static com.facebook.buck.cxx.toolchain.CxxPlatformUtils.DEFAULT_PLATFORM_FLAVOR;
import static com.facebook.buck.features.apple.projectV2.ProjectGeneratorTestUtils.assertTargetExistsAndReturnTarget;
import static com.facebook.buck.features.apple.projectV2.ProjectGeneratorTestUtils.getExpectedBuildPhasesByType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSString;
import com.facebook.buck.apple.AppleAssetCatalogBuilder;
import com.facebook.buck.apple.AppleBinaryBuilder;
import com.facebook.buck.apple.AppleBundle;
import com.facebook.buck.apple.AppleBundleBuilder;
import com.facebook.buck.apple.AppleBundleExtension;
import com.facebook.buck.apple.AppleConfig;
import com.facebook.buck.apple.AppleDependenciesCache;
import com.facebook.buck.apple.AppleLibraryBuilder;
import com.facebook.buck.apple.AppleLibraryDescriptionArg;
import com.facebook.buck.apple.AppleResourceBuilder;
import com.facebook.buck.apple.AppleTestBuilder;
import com.facebook.buck.apple.CoreDataModelBuilder;
import com.facebook.buck.apple.SceneKitAssetsBuilder;
import com.facebook.buck.apple.XCodeDescriptions;
import com.facebook.buck.apple.XCodeDescriptionsFactory;
import com.facebook.buck.apple.clang.HeaderMap;
import com.facebook.buck.apple.xcode.xcodeproj.PBXBuildFile;
import com.facebook.buck.apple.xcode.xcodeproj.PBXBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXContainerItemProxy;
import com.facebook.buck.apple.xcode.xcodeproj.PBXFileReference;
import com.facebook.buck.apple.xcode.xcodeproj.PBXGroup;
import com.facebook.buck.apple.xcode.xcodeproj.PBXHeadersBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXProject;
import com.facebook.buck.apple.xcode.xcodeproj.PBXReference;
import com.facebook.buck.apple.xcode.xcodeproj.PBXResourcesBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXShellScriptBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXSourcesBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXTarget;
import com.facebook.buck.apple.xcode.xcodeproj.ProductType;
import com.facebook.buck.apple.xcode.xcodeproj.ProductTypes;
import com.facebook.buck.apple.xcode.xcodeproj.SourceTreePath;
import com.facebook.buck.apple.xcode.xcodeproj.XCBuildConfiguration;
import com.facebook.buck.apple.xcode.xcodeproj.XCVersionGroup;
import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.cell.TestCellBuilder;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.UserFlavor;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetGraphFactory;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.parser.buildtargetpattern.BuildTargetPattern;
import com.facebook.buck.core.parser.buildtargetpattern.BuildTargetPatternParser;
import com.facebook.buck.core.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.DefaultBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.FakeSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.SourceWithFlags;
import com.facebook.buck.core.util.graph.AbstractBottomUpTraversal;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxLibraryBuilder;
import com.facebook.buck.cxx.CxxPrecompiledHeaderBuilder;
import com.facebook.buck.cxx.CxxSource;
import com.facebook.buck.cxx.config.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.CxxPlatformUtils;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.features.halide.HalideBuckConfig;
import com.facebook.buck.features.halide.HalideLibraryBuilder;
import com.facebook.buck.features.halide.HalideLibraryDescription;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceSortedSet;
import com.facebook.buck.rules.keys.EmptyFakeBuildRule;
import com.facebook.buck.rules.keys.config.TestRuleKeyConfigurationFactory;
import com.facebook.buck.rules.macros.LocationMacro;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.macros.StringWithMacrosUtils;
import com.facebook.buck.shell.ExportFileBuilder;
import com.facebook.buck.shell.ExportFileDescriptionArg;
import com.facebook.buck.shell.GenruleBuilder;
import com.facebook.buck.swift.SwiftBuckConfig;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.timing.IncrementingFakeClock;
import com.facebook.buck.util.timing.SettableFakeClock;
import com.facebook.buck.util.types.Either;
import com.facebook.buck.util.types.Pair;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ProjectGeneratorTest {

  private static final BuildTarget TARGET_1 = BuildTargetFactory.newInstance("//example/base:one");

  private static final BuildRule RULE_1 = new EmptyFakeBuildRule(TARGET_1);
  private static final Path OUTPUT_DIRECTORY = Paths.get("_gen");
  private static final String PROJECT_NAME = "GeneratedProject";
  private static final String PROJECT_CONTAINER = PROJECT_NAME + ".xcodeproj";
  private static final Path OUTPUT_PROJECT_BUNDLE_PATH =
      OUTPUT_DIRECTORY.resolve(PROJECT_CONTAINER);
  private static final Path OUTPUT_PROJECT_FILE_PATH =
      OUTPUT_PROJECT_BUNDLE_PATH.resolve("project.pbxproj");
  private static final CxxPlatform DEFAULT_PLATFORM = CxxPlatformUtils.DEFAULT_PLATFORM;
  private static final Flavor DEFAULT_FLAVOR = InternalFlavor.of("default");
  private static final String WATCH_EXTENSION_PRODUCT_TYPE =
      "com.apple.product-type.watchkit2-extension";
  private static final Path PUBLIC_HEADER_MAP_PATH = Paths.get("buck-out/gen/_p/pub-hmap");
  private SettableFakeClock clock;
  private ProjectFilesystem projectFilesystem;
  private Cell projectCell;
  private FakeProjectFilesystem fakeProjectFilesystem;
  private HalideBuckConfig halideBuckConfig;
  private CxxBuckConfig cxxBuckConfig;
  private AppleConfig appleConfig;
  private SwiftBuckConfig swiftBuckConfig;

  @Rule public ExpectedException thrown = ExpectedException.none();
  private Path rootPath;

  @Before
  public void setUp() throws IOException {
    assumeTrue(Platform.detect() == Platform.MACOS || Platform.detect() == Platform.LINUX);
    clock = SettableFakeClock.DO_NOT_CARE;
    fakeProjectFilesystem = new FakeProjectFilesystem(clock);
    projectCell = (new TestCellBuilder()).setFilesystem(fakeProjectFilesystem).build();
    projectFilesystem = projectCell.getFilesystem();
    rootPath = projectFilesystem.getRootPath();

    // Add files and directories used to test resources.
    projectFilesystem.createParentDirs(Paths.get("foodir", "foo.png"));
    projectFilesystem.writeContentsToPath("", Paths.get("foodir", "foo.png"));
    projectFilesystem.writeContentsToPath("", Paths.get("bar.png"));
    fakeProjectFilesystem.touch(Paths.get("Base.lproj", "Bar.storyboard"));
    halideBuckConfig = HalideLibraryBuilder.createDefaultHalideConfig(fakeProjectFilesystem);

    Path buildSystemPath = AppleProjectHelper.getBuildScriptPath(fakeProjectFilesystem);

    ImmutableMap<String, ImmutableMap<String, String>> sections =
        ImmutableMap.of(
            "cxx",
            ImmutableMap.<String, String>builder()
                .put("cflags", "-Wno-deprecated -Wno-conversion")
                .put("cppflags", "-DDEBUG=1")
                .put("cxxppflags", "-DDEBUG=1")
                .put("cxxflags", "-Wundeclared-selector -Wno-objc-designated-initializers")
                .put("ldflags", "-fatal_warnings")
                .put("exported_headers_symlinks_enabled", "false")
                .put("headers_symlinks_enabled", "false")
                .build(),
            "cxx#appletvos-armv7",
            ImmutableMap.of("cflags", "-Wno-nullability-completeness"),
            "apple",
            ImmutableMap.of(
                "force_dsym_mode_in_build_with_buck",
                "false",
                AppleConfig.BUILD_SCRIPT,
                buildSystemPath.toString()),
            "swift",
            ImmutableMap.of("version", "1.23"));
    BuckConfig config =
        FakeBuckConfig.builder().setFilesystem(fakeProjectFilesystem).setSections(sections).build();
    cxxBuckConfig = new CxxBuckConfig(config);
    appleConfig = config.getView(AppleConfig.class);
    swiftBuckConfig = new SwiftBuckConfig(config);
  }

  @Test
  public void testProjectStructureWithInfoPlist() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(libraryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of(("Info.plist")))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, bundleNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    Iterable<String> childNames =
        Iterables.transform(project.getMainGroup().getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("Info.plist"));
  }

  @Test
  public void testProjectStructureWithBuildFileName() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo/myLib", "lib");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode), libraryTarget);
    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup fooGroup = PBXTestUtils.assertHasSubgroupAndReturnIt(project.getMainGroup(), "foo");
    PBXGroup myLibGroup = PBXTestUtils.assertHasSubgroupAndReturnIt(fooGroup, "myLib");
    PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(myLibGroup, "BUCK");
  }

  @Test
  public void testProjectWithSharedBundleDep() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "sharedFramework");
    BuildTarget sharedLibTarget = BuildTargetFactory.newInstance("//foo:shared#shared");

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(sharedLibTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setProductName(Optional.of("shared"))
            .build();

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("foo.c"))))
            .setDeps(ImmutableSortedSet.of(bundleTarget, sharedLibTarget))
            .build();

    TargetNode<?> sharedLibNode =
        AppleLibraryBuilder.createBuilder(sharedLibTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .build();

    ImmutableSet<TargetNode<?>> nodes = ImmutableSet.of(libraryNode, bundleNode, sharedLibNode);
    ProjectGenerator projectGenerator =
        createProjectGenerator(
            nodes,
            nodes,
            bundleTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(InternalFlavor.of("iphonesimulator-x86_64")),
            Optional.of(ImmutableMap.of(sharedLibTarget, bundleNode)));

    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();
    assertEquals(project.getMainGroup().getChildren().size(), 5);
    assertEquals(project.getTargets().size(), 3);
  }

  @Test
  public void testSwapWithSharedBundes() {
    BuildTarget sharedLibrary = BuildTargetFactory.newInstance("//foo:shared#shared");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "sharedFramework");

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(sharedLibrary)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setProductName(Optional.of("shared"))
            .build();

    TargetNode<?> sharedNode = AppleLibraryBuilder.createBuilder(sharedLibrary).build();

    ImmutableMap<BuildTarget, TargetNode<?>> sharedLibraryToBundle =
        ImmutableMap.of(sharedLibrary, bundleNode);

    FluentIterable<TargetNode<?>> input = FluentIterable.of(sharedNode);
    FluentIterable<TargetNode<?>> output =
        ProjectGenerator.swapSharedLibrariesForBundles(input, sharedLibraryToBundle);
    assertTrue(output.contains(bundleNode));
    assertFalse(output.contains(sharedNode));
    assertEquals(output.size(), 1);
  }

  @Test
  public void testProjectStructureWithDuplicateBundle() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo:lib");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo:bundle");
    BuildTarget libraryWithFlavorTarget =
        BuildTargetFactory.newInstance("//foo:lib#iphonesimulator-x86_64");
    BuildTarget bundleWithFlavorTarget =
        BuildTargetFactory.newInstance("//foo:bundle#iphonesimulator-x86_64");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();
    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(libraryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of(("Info.plist")))
            .build();

    TargetNode<?> libraryWithFlavorNode =
        AppleLibraryBuilder.createBuilder(libraryWithFlavorTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();
    TargetNode<?> bundleWithFlavorNode =
        AppleBundleBuilder.createBuilder(bundleWithFlavorTarget)
            .setBinary(libraryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of(("Info.plist")))
            .build();

    ImmutableSet<TargetNode<?>> nodes =
        ImmutableSet.of(libraryNode, bundleNode, libraryWithFlavorNode, bundleWithFlavorNode);
    ProjectGenerator projectGenerator =
        createProjectGenerator(
            nodes,
            nodes,
            bundleTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(
                InternalFlavor.of("iphonesimulator-x86_64"), InternalFlavor.of("macosx-x86_64")),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    int count = 0;
    PBXProject project = projectGenerator.getGeneratedProject();
    for (PBXTarget target : project.getTargets()) {
      if (target.getProductName().equals("bundle")) {
        count++;
      }
    }
    assertSame(count, 1);
  }

  @Test
  public void testPlatformSourcesAndHeaders() {
    SourceWithFlags androidSource =
        SourceWithFlags.builder().setSourcePath(FakeSourcePath.of("androidFile.cpp")).build();
    SourceWithFlags iOSAndSimulatorSource =
        SourceWithFlags.builder()
            .setSourcePath(FakeSourcePath.of("iOSAndSimulatorFile.cpp"))
            .build();
    SourceWithFlags macOSSource =
        SourceWithFlags.builder().setSourcePath(FakeSourcePath.of("macOSFile.cpp")).build();

    Pattern androidPattern = Pattern.compile("android");
    Pattern simulatorPattern = Pattern.compile("^iphonesim.*");
    Pattern iOSPattern = Pattern.compile("iphoneos");
    Pattern macOSPattern = Pattern.compile("macos");

    Pair<Pattern, ImmutableSortedSet<SourceWithFlags>> androidSources =
        new Pair<>(androidPattern, ImmutableSortedSet.of(androidSource));
    Pair<Pattern, ImmutableSortedSet<SourceWithFlags>> simulatorSources =
        new Pair<>(simulatorPattern, ImmutableSortedSet.of(iOSAndSimulatorSource));
    Pair<Pattern, ImmutableSortedSet<SourceWithFlags>> iOSSources =
        new Pair<>(iOSPattern, ImmutableSortedSet.of(iOSAndSimulatorSource));
    Pair<Pattern, ImmutableSortedSet<SourceWithFlags>> macOSSources =
        new Pair<>(macOSPattern, ImmutableSortedSet.of(macOSSource));

    ImmutableList.Builder<Pair<Pattern, ImmutableSortedSet<SourceWithFlags>>>
        platformSourcesbuilder = ImmutableList.builder();
    ImmutableList<Pair<Pattern, ImmutableSortedSet<SourceWithFlags>>> platformSources =
        platformSourcesbuilder
            .add(androidSources)
            .add(simulatorSources)
            .add(iOSSources)
            .add(macOSSources)
            .build();

    SourcePath androidHeader = FakeSourcePath.of("androidFile.h");
    SourcePath simulatorHeader = FakeSourcePath.of("simulatorFile.h");
    SourcePath macOSAndIOSHeader = FakeSourcePath.of("macOSAndIOSFile.h");

    Pattern androidPattern2 = Pattern.compile("android");
    Pattern simulatorPattern2 = Pattern.compile("iphonesimulator");
    Pattern iOSPattern2 = Pattern.compile("iphoneos");
    Pattern macOSPattern2 = Pattern.compile("mac");

    Pair<Pattern, SourceSortedSet> androidHeaders =
        new Pair<>(
            androidPattern2,
            SourceSortedSet.ofUnnamedSources(ImmutableSortedSet.of(androidHeader)));
    Pair<Pattern, SourceSortedSet> simulatorHeaders =
        new Pair<>(
            simulatorPattern2,
            SourceSortedSet.ofUnnamedSources(ImmutableSortedSet.of(simulatorHeader)));
    Pair<Pattern, SourceSortedSet> iOSHeaders =
        new Pair<>(
            iOSPattern2,
            SourceSortedSet.ofUnnamedSources(ImmutableSortedSet.of(macOSAndIOSHeader)));
    Pair<Pattern, SourceSortedSet> macOSHeaders =
        new Pair<>(
            macOSPattern2,
            SourceSortedSet.ofUnnamedSources(ImmutableSortedSet.of(macOSAndIOSHeader)));

    SourcePath androidAndMacHeader = FakeSourcePath.of("androidAndMacFile.h");
    SourcePath iPhoneHeader = FakeSourcePath.of("iOSAndSimulatorFile.h");

    Pattern androidPattern3 = Pattern.compile("an.*");
    Pattern iPhonePattern = Pattern.compile("iphone");

    Pair<Pattern, SourceSortedSet> androidHeaders2 =
        new Pair<>(
            androidPattern3,
            SourceSortedSet.ofNamedSources(ImmutableSortedMap.of("ignored", androidAndMacHeader)));
    Pair<Pattern, SourceSortedSet> iPhoneHeaders =
        new Pair<>(
            iPhonePattern,
            SourceSortedSet.ofNamedSources(ImmutableSortedMap.of("ignored", iPhoneHeader)));
    Pair<Pattern, SourceSortedSet> macOSHeaders2 =
        new Pair<>(
            macOSPattern2,
            SourceSortedSet.ofNamedSources(ImmutableSortedMap.of("ignored", androidAndMacHeader)));

    ImmutableList.Builder<Pair<Pattern, SourceSortedSet>> platformHeadersBuilder =
        ImmutableList.builder();
    platformHeadersBuilder.add(androidHeaders);
    platformHeadersBuilder.add(simulatorHeaders);
    platformHeadersBuilder.add(iOSHeaders);
    platformHeadersBuilder.add(macOSHeaders);
    platformHeadersBuilder.add(androidHeaders2);
    platformHeadersBuilder.add(iPhoneHeaders);
    platformHeadersBuilder.add(macOSHeaders2);
    ImmutableList<Pair<Pattern, SourceSortedSet>> platformHeaders = platformHeadersBuilder.build();

    ImmutableList.Builder<Pair<Pattern, Iterable<SourcePath>>> platformHeadersIterableBuilder =
        ImmutableList.builder();
    for (Pair<Pattern, SourceSortedSet> platformHeader : platformHeaders) {
      platformHeadersIterableBuilder.add(
          new Pair<>(
              platformHeader.getFirst(),
              ProjectGenerator.getHeaderSourcePaths(platformHeader.getSecond())));
    }
    ImmutableList<Pair<Pattern, Iterable<SourcePath>>> platformHeadersIterable =
        platformHeadersIterableBuilder.build();

    UserFlavor simulator = UserFlavor.of("iphonesimulator11.4-i386", "buck boilerplate");
    UserFlavor macOS = UserFlavor.of("macosx10-x86_64", "buck boilerplate");
    UserFlavor iOS = UserFlavor.of("iphoneos-x86_64", "buck boilerplate");

    ImmutableSet<Flavor> appleFlavors = ImmutableSet.of(simulator, iOS, macOS);

    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();

    ImmutableMap<String, ImmutableSortedSet<String>> result =
        ProjectGenerator.gatherExcludedSources(
            appleFlavors,
            platformSources,
            platformHeadersIterable,
            Paths.get(".."),
            graphBuilder.getSourcePathResolver());

    ImmutableSet.Builder<String> excludedResultsBuilder = ImmutableSet.builder();
    ImmutableSet<String> excludedResults =
        excludedResultsBuilder
            .add("'../androidFile.cpp'")
            .add("'../iOSAndSimulatorFile.cpp'")
            .add("'../macOSFile.cpp'")
            .add("'../androidFile.h'")
            .add("'../simulatorFile.h'")
            .add("'../macOSAndIOSFile.h'")
            .add("'../androidAndMacFile.h'")
            .add("'../iOSAndSimulatorFile.h'")
            .build();
    ImmutableSet.Builder<String> simulatorResultsBuilder = ImmutableSet.builder();
    ImmutableSet<String> simulatorResults =
        simulatorResultsBuilder
            .add("'../iOSAndSimulatorFile.cpp'")
            .add("'../simulatorFile.h'")
            .add("'../iOSAndSimulatorFile.h'")
            .build();
    ImmutableSet.Builder<String> iOSResultsBuilder = ImmutableSet.builder();
    ImmutableSet<String> iOSResults =
        iOSResultsBuilder
            .add("'../iOSAndSimulatorFile.cpp'")
            .add("'../macOSAndIOSFile.h'")
            .add("'../iOSAndSimulatorFile.h'")
            .build();
    ImmutableSet.Builder<String> macOSResultsBuilder = ImmutableSet.builder();
    ImmutableSet<String> macOSResults =
        macOSResultsBuilder
            .add("'../macOSFile.cpp'")
            .add("'../macOSAndIOSFile.h'")
            .add("'../androidAndMacFile.h'")
            .build();

    ImmutableMap.Builder<String, ImmutableSet<String>> expectedBuilder = ImmutableMap.builder();
    expectedBuilder.put("EXCLUDED_SOURCE_FILE_NAMES", excludedResults);
    expectedBuilder.put("INCLUDED_SOURCE_FILE_NAMES[sdk=iphoneos*][arch=x86_64]", iOSResults);
    expectedBuilder.put(
        "INCLUDED_SOURCE_FILE_NAMES[sdk=iphonesimulator*][arch=i386]", simulatorResults);
    expectedBuilder.put("INCLUDED_SOURCE_FILE_NAMES[sdk=macosx*][arch=x86_64]", macOSResults);
    ImmutableMap<String, ImmutableSet<String>> expectedResult = expectedBuilder.build();

    assertEquals(result, expectedResult);
  }

  @Test
  public void testProjectStructureWithGenruleSources() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    BuildTarget genruleTarget = BuildTargetFactory.newInstance("//foo", "genrule");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(libraryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of(("Info.plist")))
            .build();

    TargetNode<?> genruleNode =
        GenruleBuilder.newGenruleBuilder(genruleTarget)
            .setSrcs(
                ImmutableList.of(FakeSourcePath.of("foo/foo.json"), FakeSourcePath.of("bar.json")))
            .setOut("out")
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, bundleNode, genruleNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    Iterable<String> childNames =
        Iterables.transform(project.getMainGroup().getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("Info.plist"));

    childNames = Iterables.transform(project.getMainGroup().getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("bar.json"));

    PBXGroup otherFooGroup = project.getMainGroup().getOrCreateChildGroupByName("foo");
    childNames = Iterables.transform(otherFooGroup.getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("foo.json"));
  }

  @Test
  public void testProjectStructureWithExtraXcodeFiles() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .setExtraXcodeFiles(
                ImmutableList.of(FakeSourcePath.of("foo/foo.json"), FakeSourcePath.of("bar.json")))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(libraryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of(("Info.plist")))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, bundleNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    Iterable<String> childNames =
        Iterables.transform(project.getMainGroup().getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("bar.json"));

    PBXGroup fooGroup = project.getMainGroup().getOrCreateChildGroupByName("foo");
    childNames = Iterables.transform(fooGroup.getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("foo.json"));

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertSourcesNotInSourcesPhase(target, ImmutableSet.of("bar.json"));
  }

  @Test
  public void testProjectStructureWithExtraXcodeSources() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .setExtraXcodeSources(
                ImmutableList.of(FakeSourcePath.of("foo/foo.m"), FakeSourcePath.of("bar.m")))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(libraryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of(("Info.plist")))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, bundleNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    Iterable<String> childNames =
        Iterables.transform(project.getMainGroup().getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("bar.m"));

    PBXGroup fooGroup = project.getMainGroup().getOrCreateChildGroupByName("foo");
    childNames = Iterables.transform(fooGroup.getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("foo.m"));

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertHasSingleSourcesPhaseWithSourcesAndFlags(
        target,
        ImmutableMap.of(
            "foo/foo.m", Optional.empty(),
            "bar.m", Optional.empty()));
  }

  @Test
  public void testModularLibraryInterfaceMapInclusionAsDependency() throws IOException {
    BuildTarget frameworkBundleTarget = BuildTargetFactory.newInstance("//foo", "framework");
    BuildTarget frameworkLibTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget appBundleTarget = BuildTargetFactory.newInstance("//product", "app");
    BuildTarget appBinaryTarget = BuildTargetFactory.newInstance("//product", "binary");

    String configName = "Default";

    TargetNode<?> frameworkLibNode =
        AppleLibraryBuilder.createBuilder(frameworkLibTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setHeaders(
                ImmutableSortedSet.of(
                    FakeSourcePath.of("HeaderGroup1/foo.h"),
                    FakeSourcePath.of("HeaderGroup2/baz.h")))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/bar.h")))
            .setConfigs(ImmutableSortedMap.of(configName, ImmutableMap.of()))
            .setModular(true)
            .build();

    TargetNode<?> frameworkBundleNode =
        AppleBundleBuilder.createBuilder(frameworkBundleTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(frameworkLibTarget)
            .build();

    TargetNode<?> appBinaryNode =
        AppleLibraryBuilder.createBuilder(appBinaryTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setDeps(ImmutableSortedSet.of(frameworkBundleTarget))
            .setConfigs(ImmutableSortedMap.of(configName, ImmutableMap.of()))
            .build();

    TargetNode<?> appBundleNode =
        AppleBundleBuilder.createBuilder(appBundleTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(appBinaryTarget)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(frameworkLibNode, frameworkBundleNode, appBinaryNode, appBundleNode),
            ImmutableSet.of(frameworkBundleNode, appBinaryNode, appBundleNode),
            appBundleTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    assertNotNull(project);

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(4));
    assertTrue(headerSymlinkTrees.contains(Paths.get("buck-out/gen/_p/CwkbTNOBmb-pub")));

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.of("lib/bar.h", rootPath.resolve("HeaderGroup1/bar.h").toString()));
  }

  @Test
  public void testModularLibraryInterfaceInclusionInTargetItself() throws IOException {
    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setHeaders(
                ImmutableSortedSet.of(
                    FakeSourcePath.of("HeaderGroup1/foo.h"),
                    FakeSourcePath.of("HeaderGroup2/baz.h")))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/bar.h")))
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setModular(true)
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(libNode), libTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    assertNotNull(project);

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();

    // We expect a public and private header search path entry
    assertThat(headerSymlinkTrees, hasSize(2));
    // And that the public is the first
    assertThat(headerSymlinkTrees.get(0).toString(), is(equalTo("buck-out/gen/_p/CwkbTNOBmb-pub")));

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.of("lib/bar.h", rootPath.resolve("HeaderGroup1/bar.h").toString()));
  }

  @Test
  public void testModularLibraryMixedSourcesFlags() throws IOException {
    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/bar.h")))
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSwiftVersion(Optional.of("3"))
            .setModular(true)
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(libNode), libTarget);

    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();
    assertNotNull(project);
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:lib");

    ImmutableMap<String, String> settings = getBuildSettings(libTarget, target, "Debug");
    assertThat(settings.get("OTHER_SWIFT_FLAGS"), containsString("-import-underlying-module"));
    assertThat(
        settings.get("OTHER_SWIFT_FLAGS"),
        containsString(
            "-Xcc -ivfsoverlay -Xcc '$REPO_ROOT/buck-out/gen/_p/CwkbTNOBmb-pub/objc-module-overlay.yaml'"));

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(2));

    assertEquals("buck-out/gen/_p/CwkbTNOBmb-pub", headerSymlinkTrees.get(0).toString());
    assertTrue(
        projectFilesystem.isFile(headerSymlinkTrees.get(0).resolve("objc-module-overlay.yaml")));
    assertTrue(projectFilesystem.isFile(headerSymlinkTrees.get(0).resolve("lib/module.modulemap")));
    assertTrue(projectFilesystem.isFile(headerSymlinkTrees.get(0).resolve("lib/objc.modulemap")));
  }

  @Test
  public void testModularLibraryGeneratesUmbrella() throws IOException {
    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib/foo.h")))
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSwiftVersion(Optional.of("3"))
            .setModular(true)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(libNode),
            ImmutableSet.of(libNode),
            libTarget,
            ProjectGeneratorOptions.builder().setShouldGenerateMissingUmbrellaHeader(true).build(),
            ImmutableSet.of(),
            Optional.empty());

    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();
    assertNotNull(project);

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(2));
    assertEquals("buck-out/gen/_p/CwkbTNOBmb-pub", headerSymlinkTrees.get(0).toString());

    Path umbrellaHeaderPath = headerSymlinkTrees.get(0).resolve("lib/lib.h");
    Optional<String> umbrellaContents = projectFilesystem.readFileIfItExists(umbrellaHeaderPath);
    assertTrue(umbrellaContents.isPresent());
    assertFalse(umbrellaContents.get().contains("lib-Swift.h"));
  }

  @Test
  public void testModularLibraryDoesNotOverwriteExistingUmbrella() throws IOException {
    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib/lib.h")))
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSwiftVersion(Optional.of("3"))
            .setModular(true)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(libNode),
            ImmutableSet.of(libNode),
            libTarget,
            ProjectGeneratorOptions.builder().setShouldGenerateMissingUmbrellaHeader(true).build(),
            ImmutableSet.of(),
            Optional.empty());

    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();
    assertNotNull(project);

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(2));
    assertEquals("buck-out/gen/_p/CwkbTNOBmb-pub", headerSymlinkTrees.get(0).toString());
    Path umbrellaPath = headerSymlinkTrees.get(0).resolve("lib/lib.h");
    assertTrue(projectFilesystem.isSymLink(umbrellaPath));
    assertFalse(projectFilesystem.isFile(umbrellaPath));
  }

  @Test
  public void testNonModularLibraryMixedSourcesFlags() throws IOException {
    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/bar.h")))
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSwiftVersion(Optional.of("3"))
            .setModular(false)
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(libNode), libTarget);

    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();
    assertNotNull(project);
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:lib");

    ImmutableMap<String, String> settings = getBuildSettings(libTarget, target, "Debug");
    assertThat(settings.get("OTHER_SWIFT_FLAGS"), not(containsString("-import-underlying-module")));
    assertThat(
        settings.get("OTHER_SWIFT_FLAGS"),
        not(
            containsString(
                "-Xcc -ivfsoverlay -Xcc '$REPO_ROOT/buck-out/gen/_p/CwkbTNOBmb-pub/objc-module-overlay.yaml'")));

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertEquals("buck-out/gen/_p/CwkbTNOBmb-priv", headerSymlinkTrees.get(0).toString());
    assertFalse(
        projectFilesystem.isFile(headerSymlinkTrees.get(0).resolve("objc-module-overlay.yaml")));
    assertFalse(
        projectFilesystem.isFile(headerSymlinkTrees.get(0).resolve("lib/module.modulemap")));
    assertFalse(projectFilesystem.isFile(headerSymlinkTrees.get(0).resolve("lib/objc.modulemap")));
  }

  @Test
  public void testModularFrameworkBuildSettings() throws IOException {
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "framework");
    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");

    String configName = "Default";

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setHeaders(
                ImmutableSortedSet.of(
                    FakeSourcePath.of("HeaderGroup1/foo.h"),
                    FakeSourcePath.of("HeaderGroup2/baz.h")))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/bar.h")))
            .setConfigs(ImmutableSortedMap.of(configName, ImmutableMap.of()))
            .setModular(true)
            .build();

    TargetNode<?> frameworkNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(libTarget)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(libNode, frameworkNode),
            ImmutableSet.of(frameworkNode),
            bundleTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget libPBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:framework");

    ImmutableMap<String, String> buildSettings =
        getBuildSettings(bundleTarget, libPBXTarget, configName);

    assertEquals(
        "USE_HEADERMAP must be turned on for modular framework targets "
            + "so that Xcode generates VFS overlays",
        "YES",
        buildSettings.get("USE_HEADERMAP"));
    assertEquals(
        "CLANG_ENABLE_MODULES must be turned on for modular framework targets"
            + "so that Xcode generates VFS overlays",
        "YES",
        buildSettings.get("CLANG_ENABLE_MODULES"));
    assertEquals(
        "DEFINES_MODULE must be turned on for modular framework targets",
        "YES",
        buildSettings.get("DEFINES_MODULE"));
  }

  @Test
  public void testModularFrameworkHeadersInHeadersBuildPhase() throws IOException {
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "framework");
    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");

    String exportedHeaderName = "bar.h";

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setHeaders(
                ImmutableSortedSet.of(
                    FakeSourcePath.of("HeaderGroup1/foo.h"),
                    FakeSourcePath.of("HeaderGroup2/baz.h")))
            .setExportedHeaders(
                ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/" + exportedHeaderName)))
            .setModular(true)
            .build();

    TargetNode<?> frameworkNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(libTarget)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libNode, frameworkNode), libTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:framework");

    List<PBXBuildPhase> headersPhases = target.getBuildPhases();

    headersPhases.removeIf(input -> !(input instanceof PBXHeadersBuildPhase));
    assertEquals(1, headersPhases.size());

    PBXHeadersBuildPhase headersPhase = (PBXHeadersBuildPhase) headersPhases.get(0);
    List<PBXBuildFile> headers = headersPhase.getFiles();
    assertEquals(1, headers.size());

    PBXFileReference headerReference = (PBXFileReference) headers.get(0).getFileRef();
    assertNotNull(headerReference);
    assertEquals(headerReference.getName(), exportedHeaderName);
  }

  @Test
  public void testAppleLibraryHeaderGroupsWithHeaderSymlinkTrees() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setHeaders(
                ImmutableSortedSet.of(
                    FakeSourcePath.of("HeaderGroup1/foo.h"),
                    FakeSourcePath.of("HeaderGroup2/baz.h")))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/bar.h")))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("iphone.*"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/foo1.h"))))
                    // Sources that do not match the pattern still appear in the project. Xcode
                    // ignores
                    // them based on the environment variables that get set in xcconfig
                    // EXCLUDED_SOURCE_FILE_NAMES
                    // and INCLUDED_SOURCE_FILE_NAMES
                    .add(
                        Pattern.compile("android.*"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/foo2.h"))))
                    .build())
            .setExportedPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("iphone.*"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup2/foo3.h"))))
                    .build())
            .build();

    UserFlavor simulator = UserFlavor.of("iphonesimulator11.4-i386", "Testing flavor");

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node),
            ImmutableSet.of(node),
            buildTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(simulator),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    PBXGroup group1 = project.getMainGroup().getOrCreateChildGroupByName("HeaderGroup1");
    assertThat(group1.getChildren(), hasSize(4));
    PBXFileReference fileRefFoo = (PBXFileReference) Iterables.get(group1.getChildren(), 0);
    assertEquals("bar.h", fileRefFoo.getName());
    PBXFileReference fileRefBar = (PBXFileReference) Iterables.get(group1.getChildren(), 1);
    assertEquals("foo.h", fileRefBar.getName());
    PBXFileReference fileRefFoo1 = (PBXFileReference) Iterables.get(group1.getChildren(), 2);
    assertEquals("foo1.h", fileRefFoo1.getName());
    PBXFileReference fileRefFoo2 = (PBXFileReference) Iterables.get(group1.getChildren(), 3);
    assertEquals("foo2.h", fileRefFoo2.getName());

    PBXGroup group2 = project.getMainGroup().getOrCreateChildGroupByName("HeaderGroup2");
    assertEquals("HeaderGroup2", group2.getName());
    assertThat(group2.getChildren(), hasSize(2));
    PBXFileReference fileRefBaz = (PBXFileReference) Iterables.get(group2.getChildren(), 0);
    assertEquals("baz.h", fileRefBaz.getName());
    PBXFileReference fileRefFoo3 = (PBXFileReference) Iterables.get(group2.getChildren(), 1);
    assertEquals("foo3.h", fileRefFoo3.getName());

    // There should be no PBXHeadersBuildPhase in the 'Buck header map mode'.
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:lib");
    assertEquals(
        0,
        target.getBuildPhases().stream()
            .filter(input -> input instanceof PBXHeadersBuildPhase)
            .count());

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertEquals("buck-out/gen/_p/CwkbTNOBmb-priv", headerSymlinkTrees.get(0).toString());
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.<String, String>builder()
            .put("lib/foo.h", "HeaderGroup1/foo.h")
            .put("lib/baz.h", "HeaderGroup2/baz.h")
            .put("foo.h", "HeaderGroup1/foo.h")
            .put("bar.h", "HeaderGroup1/bar.h")
            .put("baz.h", "HeaderGroup2/baz.h")
            .put("lib/foo1.h", "HeaderGroup1/foo1.h")
            .put("foo1.h", "HeaderGroup1/foo1.h")
            .put("lib/foo2.h", "HeaderGroup1/foo2.h")
            .put("foo2.h", "HeaderGroup1/foo2.h")
            .put("foo3.h", "HeaderGroup2/foo3.h")
            .build());

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.<String, String>builder()
            .put("lib/bar.h", "HeaderGroup1/bar.h")
            .put("lib/foo3.h", "HeaderGroup2/foo3.h")
            .build());

    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Default");

    assertEquals(
        "'../HeaderGroup1/foo1.h' '../HeaderGroup1/foo2.h' '../HeaderGroup2/foo3.h'",
        buildSettings.get("EXCLUDED_SOURCE_FILE_NAMES"));
    assertEquals(
        "'../HeaderGroup1/foo1.h' '../HeaderGroup2/foo3.h'",
        buildSettings.get("INCLUDED_SOURCE_FILE_NAMES[sdk=iphonesimulator*][arch=i386]"));
  }

  @Test
  public void testAppleLibraryHeaderGroupsWithMappedHeaders() throws IOException {
    BuildTarget privateGeneratedTarget = BuildTargetFactory.newInstance("//foo", "generated1.h");
    BuildTarget publicGeneratedTarget = BuildTargetFactory.newInstance("//foo", "generated2.h");

    TargetNode<?> privateGeneratedNode = new ExportFileBuilder(privateGeneratedTarget).build();
    TargetNode<?> publicGeneratedNode = new ExportFileBuilder(publicGeneratedTarget).build();

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setHeaders(
                ImmutableSortedMap.of(
                    "any/name.h", FakeSourcePath.of("HeaderGroup1/foo.h"),
                    "different/name.h", FakeSourcePath.of("HeaderGroup2/baz.h"),
                    "one/more/name.h", DefaultBuildTargetSourcePath.of(privateGeneratedTarget)))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("iphone.*"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name1.h", FakeSourcePath.of("HeaderGroup1/foo1.h"))))
                    .build())
            .setExportedHeaders(
                ImmutableSortedMap.of(
                    "yet/another/name.h",
                    FakeSourcePath.of("HeaderGroup1/bar.h"),
                    "and/one/more.h",
                    DefaultBuildTargetSourcePath.of(publicGeneratedTarget)))
            .setExportedPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("android.*"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name2.h", FakeSourcePath.of("HeaderGroup2/foo2.h"))))
                    .build())
            .build();

    UserFlavor simulator = UserFlavor.of("iphonesimulator11.4-i386", "Testing flavor");

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node, privateGeneratedNode, publicGeneratedNode),
            ImmutableSet.of(node, privateGeneratedNode, publicGeneratedNode),
            buildTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(simulator),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    PBXGroup group1 = project.getMainGroup().getOrCreateChildGroupByName("HeaderGroup1");
    assertThat(group1.getChildren(), hasSize(3));
    PBXFileReference fileRefFoo = (PBXFileReference) Iterables.get(group1.getChildren(), 0);
    assertEquals("bar.h", fileRefFoo.getName());
    PBXFileReference fileRefFoo1 = (PBXFileReference) Iterables.get(group1.getChildren(), 2);
    assertEquals("foo1.h", fileRefFoo1.getName());
    PBXFileReference fileRefBar = (PBXFileReference) Iterables.get(group1.getChildren(), 1);
    assertEquals("foo.h", fileRefBar.getName());

    PBXGroup group2 = project.getMainGroup().getOrCreateChildGroupByName("HeaderGroup2");
    assertThat(group2.getChildren(), hasSize(2));
    PBXFileReference fileRefBaz = (PBXFileReference) Iterables.get(group2.getChildren(), 0);
    assertEquals("baz.h", fileRefBaz.getName());
    PBXFileReference fileRefFoo2 = (PBXFileReference) Iterables.get(group2.getChildren(), 1);
    assertEquals("foo2.h", fileRefFoo2.getName());

    PBXGroup group3 = project.getMainGroup().getOrCreateChildGroupByName("foo");
    PBXGroup generatedGroup = group3.getOrCreateChildGroupByName("GENERATED-foo");
    PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(generatedGroup, "generated1.h");
    PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(generatedGroup, "generated2.h");

    // There should be no PBXHeadersBuildPhase in the 'Buck header map mode'.
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:lib");
    assertEquals(
        0,
        target.getBuildPhases().stream()
            .filter(input -> input instanceof PBXHeadersBuildPhase)
            .count());

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.of(
            "yet/another/name.h", absolutePathForHeader("HeaderGroup1/bar.h"),
            "and/one/more.h", absolutePathForHeader("foo/generated2.h"),
            "any/name2.h", absolutePathForHeader("HeaderGroup2/foo2.h")));

    assertEquals("buck-out/gen/_p/CwkbTNOBmb-priv", headerSymlinkTrees.get(0).toString());
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.of(
            "any/name.h", absolutePathForHeader("HeaderGroup1/foo.h"),
            "different/name.h", absolutePathForHeader("HeaderGroup2/baz.h"),
            "one/more/name.h", absolutePathForHeader("foo/generated1.h"),
            "any/name1.h", absolutePathForHeader("HeaderGroup1/foo1.h")));

    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Default");

    assertEquals(
        "'../HeaderGroup1/foo1.h' '../HeaderGroup2/foo2.h'",
        buildSettings.get("EXCLUDED_SOURCE_FILE_NAMES"));
    assertEquals(
        "'../HeaderGroup1/foo1.h'",
        buildSettings.get("INCLUDED_SOURCE_FILE_NAMES[sdk=iphonesimulator*][arch=i386]"));
  }

  @Test
  public void testCxxLibraryWithListsOfHeaders() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo/dir1/bar.h")))
            .setExportedPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("foo/HeaderGroup2/foo3.h"))))
                    .build())
            .setHeaders(
                ImmutableSortedSet.of(
                    FakeSourcePath.of("foo/dir1/foo.h"), FakeSourcePath.of("foo/dir2/baz.h")))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("foo/HeaderGroup1/foo1.h"))))
                    .build())
            .setSrcs(ImmutableSortedSet.of())
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();

    // We expect one private header symlink tree
    assertThat(headerSymlinkTrees, hasSize(1));

    assertThat(
        headerSymlinkTrees.get(0).toString(), is(equalTo("buck-out/gen/_p/CwkbTNOBmb-priv")));
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.<String, String>builder()
            .put("foo/dir1/foo.h", absolutePathForHeader("foo/dir1/foo.h"))
            .put("foo/dir2/baz.h", absolutePathForHeader("foo/dir2/baz.h"))
            .put("foo/HeaderGroup1/foo1.h", absolutePathForHeader("foo/HeaderGroup1/foo1.h"))
            .build());

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.<String, String>builder()
            .put("foo/dir1/bar.h", absolutePathForHeader("foo/dir1/bar.h"))
            .put("foo/HeaderGroup2/foo3.h", absolutePathForHeader("foo/HeaderGroup2/foo3.h"))
            .build());
  }

  private String absolutePathForHeader(String relativePath) {
    return rootPath.resolve(relativePath).toString();
  }

  @Test
  public void testCxxLibraryWithoutHeadersSymLinks() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo/dir1/bar.h")))
            .setExportedPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("foo/HeaderGroup2/foo3.h"))))
                    .build())
            .setHeaders(
                ImmutableSortedSet.of(
                    FakeSourcePath.of("foo/dir1/foo.h"), FakeSourcePath.of("foo/dir2/baz.h")))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("foo/HeaderGroup1/foo1.h"))))
                    .build())
            .setSrcs(ImmutableSortedSet.of())
            .setXcodePublicHeadersSymlinks(false)
            .setXcodePrivateHeadersSymlinks(false)
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertThat(
        headerSymlinkTrees.get(0).toString(), is(equalTo("buck-out/gen/_p/CwkbTNOBmb-priv")));
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.<String, String>builder()
            .put("foo/dir1/foo.h", "foo/dir1/foo.h")
            .put("foo/dir2/baz.h", "foo/dir2/baz.h")
            .put("foo/HeaderGroup1/foo1.h", "foo/HeaderGroup1/foo1.h")
            .build());
  }

  @Test
  public void testCxxLibraryWithListsOfHeadersAndCustomNamespace() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo/dir1/bar.h")))
            .setExportedPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("foo/HeaderGroup2/foo3.h"))))
                    .build())
            .setHeaders(
                ImmutableSortedSet.of(
                    FakeSourcePath.of("foo/dir1/foo.h"), FakeSourcePath.of("foo/dir2/baz.h")))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofUnnamedSources(
                            ImmutableSortedSet.of(FakeSourcePath.of("foo/HeaderGroup1/foo1.h"))))
                    .build())
            .setSrcs(ImmutableSortedSet.of())
            .setHeaderNamespace("name/space")
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertThat(
        headerSymlinkTrees.get(0).toString(), is(equalTo("buck-out/gen/_p/CwkbTNOBmb-priv")));
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.<String, String>builder()
            .put("name/space/dir1/foo.h", absolutePathForHeader("foo/dir1/foo.h"))
            .put("name/space/dir2/baz.h", absolutePathForHeader("foo/dir2/baz.h"))
            .put("name/space/HeaderGroup1/foo1.h", absolutePathForHeader("foo/HeaderGroup1/foo1.h"))
            .build());

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.<String, String>builder()
            .put("name/space/dir1/bar.h", absolutePathForHeader("foo/dir1/bar.h"))
            .put("name/space/HeaderGroup2/foo3.h", absolutePathForHeader("foo/HeaderGroup2/foo3.h"))
            .build());
  }

  @Test
  public void testCxxLibraryHeaderGroupsWithMapsOfHeaders() throws IOException {
    BuildTarget privateGeneratedTarget = BuildTargetFactory.newInstance("//foo", "generated1.h");
    BuildTarget publicGeneratedTarget = BuildTargetFactory.newInstance("//foo", "generated2.h");

    TargetNode<?> privateGeneratedNode = new ExportFileBuilder(privateGeneratedTarget).build();
    TargetNode<?> publicGeneratedNode = new ExportFileBuilder(publicGeneratedTarget).build();

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedHeaders(
                ImmutableSortedMap.of(
                    "yet/another/name.h",
                    FakeSourcePath.of("foo/dir1/bar.h"),
                    "and/one/more.h",
                    DefaultBuildTargetSourcePath.of(publicGeneratedTarget)))
            .setExportedPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name2.h", FakeSourcePath.of("HeaderGroup2/foo2.h"))))
                    .build())
            .setHeaders(
                ImmutableSortedMap.of(
                    "any/name.h", FakeSourcePath.of("foo/dir1/foo.h"),
                    "different/name.h", FakeSourcePath.of("foo/dir2/baz.h"),
                    "one/more/name.h", DefaultBuildTargetSourcePath.of(privateGeneratedTarget)))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name1.h", FakeSourcePath.of("HeaderGroup1/foo1.h"))))
                    .build())
            .setSrcs(ImmutableSortedSet.of())
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node, privateGeneratedNode, publicGeneratedNode),
            publicGeneratedTarget);

    projectGenerator.createXcodeProjects();

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertThat(
        headerSymlinkTrees.get(0).toString(), is(equalTo("buck-out/gen/_p/CwkbTNOBmb-priv")));
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.of(
            "foo/any/name.h", "foo/dir1/foo.h",
            "foo/different/name.h", "foo/dir2/baz.h",
            "foo/one/more/name.h", "foo/generated1.h",
            "foo/any/name1.h", "HeaderGroup1/foo1.h"));

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.of(
            "foo/yet/another/name.h", "foo/dir1/bar.h",
            "foo/and/one/more.h", "foo/generated2.h",
            "foo/any/name2.h", "HeaderGroup2/foo2.h"));
  }

  @Test
  public void testCxxLibraryHeaderGroupsWithMapsOfHeadersAndNotMatchingPlatform()
      throws IOException {
    BuildTarget privateGeneratedTarget = BuildTargetFactory.newInstance("//foo", "generated1.h");
    BuildTarget publicGeneratedTarget = BuildTargetFactory.newInstance("//foo", "generated2.h");

    TargetNode<?> privateGeneratedNode = new ExportFileBuilder(privateGeneratedTarget).build();
    TargetNode<?> publicGeneratedNode = new ExportFileBuilder(publicGeneratedTarget).build();

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedHeaders(
                ImmutableSortedMap.of(
                    "yet/another/name.h",
                    FakeSourcePath.of("foo/dir1/bar.h"),
                    "and/one/more.h",
                    DefaultBuildTargetSourcePath.of(publicGeneratedTarget)))
            .setExportedPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name2.h", FakeSourcePath.of("HeaderGroup2/foo2.h"))))
                    .add(
                        Pattern.compile("unknown"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name2.h", FakeSourcePath.of("HeaderGroup2/unknown/foo2.h"))))
                    .build())
            .setHeaders(
                ImmutableSortedMap.of(
                    "any/name.h", FakeSourcePath.of("foo/dir1/foo.h"),
                    "different/name.h", FakeSourcePath.of("foo/dir2/baz.h"),
                    "one/more/name.h", DefaultBuildTargetSourcePath.of(privateGeneratedTarget)))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name1.h", FakeSourcePath.of("HeaderGroup1/foo1.h"))))
                    .build())
            .setSrcs(ImmutableSortedSet.of())
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node, privateGeneratedNode, publicGeneratedNode),
            publicGeneratedTarget);

    projectGenerator.createXcodeProjects();

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertThat(
        headerSymlinkTrees.get(0).toString(), is(equalTo("buck-out/gen/_p/CwkbTNOBmb-priv")));

    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.of(
            "foo/any/name.h", "foo/dir1/foo.h",
            "foo/different/name.h", "foo/dir2/baz.h",
            "foo/one/more/name.h", "foo/generated1.h",
            "foo/any/name1.h", "HeaderGroup1/foo1.h"));

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.of(
            "foo/yet/another/name.h", "foo/dir1/bar.h",
            "foo/and/one/more.h", "foo/generated2.h",
            "foo/any/name2.h", "HeaderGroup2/foo2.h"));
  }

  @Test
  public void testCxxLibraryHeaderGroupsWithMapsOfHeadersAndCustomNamespace() throws IOException {
    BuildTarget privateGeneratedTarget = BuildTargetFactory.newInstance("//foo", "generated1.h");
    BuildTarget publicGeneratedTarget = BuildTargetFactory.newInstance("//foo", "generated2.h");

    TargetNode<?> privateGeneratedNode = new ExportFileBuilder(privateGeneratedTarget).build();
    TargetNode<?> publicGeneratedNode = new ExportFileBuilder(publicGeneratedTarget).build();

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedHeaders(
                ImmutableSortedMap.of(
                    "yet/another/name.h",
                    FakeSourcePath.of("foo/dir1/bar.h"),
                    "and/one/more.h",
                    DefaultBuildTargetSourcePath.of(publicGeneratedTarget)))
            .setExportedPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name2.h", FakeSourcePath.of("HeaderGroup2/foo2.h"))))
                    .build())
            .setHeaders(
                ImmutableSortedMap.of(
                    "any/name.h", FakeSourcePath.of("foo/dir1/foo.h"),
                    "different/name.h", FakeSourcePath.of("foo/dir2/baz.h"),
                    "one/more/name.h", DefaultBuildTargetSourcePath.of(privateGeneratedTarget)))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("default"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of(
                                "any/name1.h", FakeSourcePath.of("HeaderGroup1/foo1.h"))))
                    .build())
            .setSrcs(ImmutableSortedSet.of())
            .setHeaderNamespace("name/space")
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node, privateGeneratedNode, publicGeneratedNode),
            publicGeneratedTarget);

    projectGenerator.createXcodeProjects();

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertThat(
        headerSymlinkTrees.get(0).toString(), is(equalTo("buck-out/gen/_p/CwkbTNOBmb-priv")));
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.of(
            "name/space/any/name.h", "foo/dir1/foo.h",
            "name/space/different/name.h", "foo/dir2/baz.h",
            "name/space/one/more/name.h", "foo/generated1.h",
            "name/space/any/name1.h", "HeaderGroup1/foo1.h"));

    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.of(
            "name/space/yet/another/name.h", "foo/dir1/bar.h",
            "name/space/and/one/more.h", "foo/generated2.h",
            "name/space/any/name2.h", "HeaderGroup2/foo2.h"));
  }

  @Test
  public void testHeaderSymlinkTreesAreRegeneratedWhenKeyChanges() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setHeaders(ImmutableSortedMap.of("key.h", FakeSourcePath.of("value.h")))
            .setPlatformHeaders(
                PatternMatchedCollection.<SourceSortedSet>builder()
                    .add(
                        Pattern.compile("iphone.*"),
                        SourceSortedSet.ofNamedSources(
                            ImmutableSortedMap.of("key1.h", FakeSourcePath.of("value1.h"))))
                    .build())
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertEquals("buck-out/gen/_p/CwkbTNOBmb-priv", headerSymlinkTrees.get(0).toString());
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"),
        ImmutableMap.of("key.h", "value.h", "key1.h", "value1.h"));

    node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setHeaders(ImmutableSortedMap.of("new-key.h", FakeSourcePath.of("value.h")))
            .build();

    projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertEquals("buck-out/gen/_p/CwkbTNOBmb-priv", headerSymlinkTrees.get(0).toString());
    assertFalse(
        projectFilesystem.isSymLink(
            Paths.get("buck-out/gen/foo/lib-private-header-symlink-tree/key.h")));
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"), ImmutableMap.of("new-key.h", "value.h"));
  }

  @Test
  public void testHeaderSymlinkTreesAreRegeneratedWhenValueChanges() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setHeaders(ImmutableSortedMap.of("key.h", FakeSourcePath.of("value.h")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertEquals("buck-out/gen/_p/CwkbTNOBmb-priv", headerSymlinkTrees.get(0).toString());
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"), ImmutableMap.of("key.h", "value.h"));

    node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setSrcs(ImmutableSortedSet.of())
            .setHeaders(ImmutableSortedMap.of("key.h", FakeSourcePath.of("new-value.h")))
            .build();

    projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();
    assertThat(headerSymlinkTrees, hasSize(1));

    assertEquals("buck-out/gen/_p/CwkbTNOBmb-priv", headerSymlinkTrees.get(0).toString());
    assertThatHeaderMapWithoutSymLinksContains(
        Paths.get("buck-out/gen/_p/CwkbTNOBmb-priv"), ImmutableMap.of("key.h", "new-value.h"));
  }

  @Test
  public void testHeaderSymlinkTreesWithHeadersVisibleForTesting() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.h"), ImmutableList.of("public")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.h"))))
            .setTests(ImmutableSortedSet.of(testTarget))
            .build();

    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, testNode), testTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget testPBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:test");

    ImmutableMap<String, String> buildSettings =
        getBuildSettings(testTarget, testPBXTarget, "Default");

    assertEquals(
        "test binary should use header symlink trees for both public and non-public headers "
            + "of the tested library in HEADER_SEARCH_PATHS",
        "$(inherited) "
            + String.format("%s/buck-out/gen/_p/LpygK8zq5F-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/CwkbTNOBmb-priv/.hmap", rootPath),
        buildSettings.get("HEADER_SEARCH_PATHS"));

    assertEquals(
        "USER_HEADER_SEARCH_PATHS should not be set",
        null,
        buildSettings.get("USER_HEADER_SEARCH_PATHS"));
  }

  @Test
  public void testAbsoluteHeaderMapPaths() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.h"), ImmutableList.of("public")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.h"))))
            .setTests(ImmutableSortedSet.of(testTarget))
            .build();

    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();

    ImmutableSet<TargetNode<?>> allNodes = ImmutableSet.of(libraryNode, testNode);

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            allNodes,
            allNodes,
            libraryTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget testPBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:test");

    ImmutableMap<String, String> buildSettings =
        getBuildSettings(testTarget, testPBXTarget, "Default");

    Path currentDirectory = Paths.get(".").toAbsolutePath();
    assertEquals(
        "test binary should use header symlink trees for both public and non-public headers "
            + "of the tested library in HEADER_SEARCH_PATHS",
        "$(inherited) "
            + currentDirectory
                .resolve("buck-out/gen/_p/LpygK8zq5F-priv/.hmap")
                .normalize()
                .toString()
            + " "
            + currentDirectory.resolve("buck-out/gen/_p/pub-hmap/.hmap").normalize().toString()
            + " "
            + currentDirectory
                .resolve("buck-out/gen/_p/CwkbTNOBmb-priv/.hmap")
                .normalize()
                .toString(),
        buildSettings.get("HEADER_SEARCH_PATHS"));
    assertEquals(
        "USER_HEADER_SEARCH_PATHS should not be set",
        null,
        buildSettings.get("USER_HEADER_SEARCH_PATHS"));
  }

  @Test
  public void testHeaderSymlinkTreesWithHeadersVisibleForTestingWithModuleOverrides()
      throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("Foo.swift")),
                    SourceWithFlags.of(FakeSourcePath.of("foo.h"), ImmutableList.of("public")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.h"))))
            .setTests(ImmutableSortedSet.of(testTarget))
            .setSwiftVersion(Optional.of("3"))
            .setModular(true)
            .build();

    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, testNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget testPBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:test");

    ImmutableMap<String, String> buildSettings =
        getBuildSettings(testTarget, testPBXTarget, "Default");

    assertThat(
        buildSettings.get("OTHER_CFLAGS"),
        containsString(
            "-ivfsoverlay '$REPO_ROOT/buck-out/gen/_p/CwkbTNOBmb-pub/testing-overlay.yaml'"));
    assertThat(
        buildSettings.get("OTHER_CPLUSPLUSFLAGS"),
        containsString(
            "-ivfsoverlay '$REPO_ROOT/buck-out/gen/_p/CwkbTNOBmb-pub/testing-overlay.yaml'"));

    List<Path> headerSymlinkTrees = projectGenerator.getGeneratedHeaderSymlinkTrees();

    // We expect 2 private symlink paths and one public one with testing modulemaps/overlays.
    assertThat(headerSymlinkTrees, hasSize(3));

    Path libSymlinktreePublic = Paths.get("buck-out/gen/_p/CwkbTNOBmb-pub");
    assertTrue(headerSymlinkTrees.contains(libSymlinktreePublic));
    assertTrue(projectFilesystem.isFile(libSymlinktreePublic.resolve("testing-overlay.yaml")));
    assertTrue(projectFilesystem.isFile(libSymlinktreePublic.resolve("lib/testing.modulemap")));
    assertEquals(
        projectFilesystem.readFileIfItExists(libSymlinktreePublic.resolve("lib/testing.modulemap")),
        Optional.of(""));
  }

  @Test
  public void testHeaderSymlinkTreesWithTestsAndLibraryBundles() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.h"), ImmutableList.of("public")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.h"))))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(libraryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setTests(ImmutableSortedSet.of(testTarget))
            .build();

    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(bundleTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, bundleNode, testNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget testPBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:test");

    ImmutableMap<String, String> buildSettings =
        getBuildSettings(testTarget, testPBXTarget, "Default");

    assertEquals(
        "test binary should use header symlink trees for both public and non-public headers "
            + "of the tested library in HEADER_SEARCH_PATHS",
        "$(inherited) "
            + String.format("%s/buck-out/gen/_p/LpygK8zq5F-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/CwkbTNOBmb-priv/.hmap", rootPath),
        buildSettings.get("HEADER_SEARCH_PATHS"));
    assertEquals(
        "USER_HEADER_SEARCH_PATHS should not be set",
        null,
        buildSettings.get("USER_HEADER_SEARCH_PATHS"));
  }

  @Test
  public void testHeaderSymlinkTreesWithTestsAndBinaryBundles() throws IOException {
    BuildTarget binaryTarget = BuildTargetFactory.newInstance("//foo", "bin");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");

    TargetNode<?> binaryNode =
        AppleBinaryBuilder.createBuilder(binaryTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.h"), ImmutableList.of("public")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.h"))))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(binaryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setTests(ImmutableSortedSet.of(testTarget))
            .build();

    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(bundleTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(binaryNode, bundleNode, testNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget testPBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:test");

    ImmutableMap<String, String> buildSettings =
        getBuildSettings(testTarget, testPBXTarget, "Default");

    assertEquals(
        "test binary should use header symlink trees for both public and non-public headers "
            + "of the tested binary in HEADER_SEARCH_PATHS",
        "$(inherited) "
            + String.format("%s/buck-out/gen/_p/LpygK8zq5F-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/4UdYl649ee-priv/.hmap", rootPath),
        buildSettings.get("HEADER_SEARCH_PATHS"));
    assertEquals(
        "USER_HEADER_SEARCH_PATHS should not be set",
        null,
        buildSettings.get("USER_HEADER_SEARCH_PATHS"));
  }

  private void assertThatHeaderSymlinkTreeContains(Path root, ImmutableMap<String, String> content)
      throws IOException {
    // Read the tree's header map.
    byte[] headerMapBytes;
    try (InputStream headerMapInputStream =
        projectFilesystem.newFileInputStream(root.resolve(".hmap"))) {
      headerMapBytes = ByteStreams.toByteArray(headerMapInputStream);
    }
    HeaderMap headerMap = HeaderMap.deserialize(headerMapBytes);
    assertNotNull(headerMap);
    assertThat(headerMap.getNumEntries(), equalTo(content.size()));
    for (Map.Entry<String, String> entry : content.entrySet()) {
      String key = entry.getKey();
      Path link = root.resolve(Paths.get(key));
      Path target = Paths.get(entry.getValue()).toAbsolutePath();
      // Check the filesystem symlink
      assertTrue(projectFilesystem.isSymLink(link));
      assertEquals(target, projectFilesystem.readSymLink(link));

      // Check the header map
      assertThat(
          projectFilesystem.getBuckPaths().getConfiguredBuckOut().resolve(headerMap.lookup(key)),
          equalTo(link));
    }
  }

  private HeaderMap getHeaderMapInDir(Path root) throws IOException {
    // Read the tree's header map.
    byte[] headerMapBytes;
    try (InputStream headerMapInputStream =
        projectFilesystem.newFileInputStream(root.resolve(".hmap"))) {
      headerMapBytes = ByteStreams.toByteArray(headerMapInputStream);
    }
    return HeaderMap.deserialize(headerMapBytes);
  }

  private void assertThatHeaderMapWithoutSymLinksIsEmpty(Path root) throws IOException {
    HeaderMap headerMap = getHeaderMapInDir(root);
    assertNotNull(headerMap);
    assertEquals(headerMap.getNumEntries(), 0);
  }

  private void assertThatHeaderMapWithoutSymLinksContains(
      Path root, ImmutableMap<String, String> content) throws IOException {
    HeaderMap headerMap = getHeaderMapInDir(root);
    assertNotNull(headerMap);
    assertThat(headerMap.getNumEntries(), equalTo(content.size()));
    for (Map.Entry<String, String> entry : content.entrySet()) {
      String key = entry.getKey();
      Path target = Paths.get(entry.getValue()).toAbsolutePath();
      // Check the header map
      assertThat(headerMap.lookup(key), equalTo(target.toString()));
    }
  }

  @Test
  public void testAppleLibraryRule() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("RandomConfig", ImmutableMap.of()))
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.m"), ImmutableList.of("-foo")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.m"))))
            .setExtraXcodeSources(ImmutableList.of(FakeSourcePath.of("libsomething.a")))
            .setHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertThat(target.getProductType(), equalTo(ProductTypes.STATIC_LIBRARY));

    assertHasConfigurations(target, "RandomConfig");
    assertEquals("Should have exact number of build phases", 2, target.getBuildPhases().size());
    assertHasSingleSourcesPhaseWithSourcesAndFlags(
        target,
        ImmutableMap.of(
            "foo.m", Optional.of("-foo"),
            "bar.m", Optional.empty(),
            "libsomething.a", Optional.empty()));

    // this target should not have an asset catalog build phase
    assertTrue(
        FluentIterable.from(target.getBuildPhases())
            .filter(PBXResourcesBuildPhase.class)
            .isEmpty());
  }

  @Test
  public void testHalideLibraryRule() throws IOException {
    BuildTarget compilerTarget =
        BuildTargetFactory.newInstance(
            "//foo", "lib", HalideLibraryDescription.HALIDE_COMPILER_FLAVOR);
    TargetNode<?> compiler =
        new HalideLibraryBuilder(compilerTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("main.cpp")),
                    SourceWithFlags.of(FakeSourcePath.of("filter.cpp"))))
            .build();

    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> lib = new HalideLibraryBuilder(libTarget).build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(compiler, lib), libTarget);
    ProjectGenerator.Result result = projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertHasConfigurations(target, "Debug", "Release", "Profile");

    Iterable<PBXShellScriptBuildPhase> shellScriptBuildPhases =
        getExpectedBuildPhasesByType(target, PBXShellScriptBuildPhase.class, 1);
    PBXShellScriptBuildPhase scriptPhase = shellScriptBuildPhases.iterator().next();
    assertEquals(0, scriptPhase.getInputPaths().size());
    assertEquals(0, scriptPhase.getOutputPaths().size());

    // Note that we require that both the Halide "compiler" and the unflavored
    // library target are present in the requiredBuildTargets, so that both the
    // compiler and the generated header for the pipeline will be available for
    // use by the Xcode compilation step.
    ImmutableSet<BuildTarget> requiredBuildTargets = result.requiredBuildTargets;
    assertTrue(requiredBuildTargets.contains(compilerTarget));
    assertThat(
        requiredBuildTargets,
        hasItem(
            libTarget.withFlavors(
                HalideLibraryDescription.HALIDE_COMPILE_FLAVOR, DEFAULT_PLATFORM_FLAVOR)));
  }

  @Test
  public void testCxxLibraryRule() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> cxxNode =
        new CxxLibraryBuilder(buildTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.cpp"), ImmutableList.of("-foo")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.cpp"))))
            .setHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(cxxNode), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertThat(target.getProductType(), equalTo(ProductTypes.STATIC_LIBRARY));

    assertHasConfigurations(target, "Debug", "Release", "Profile");
    assertEquals("Should have exact number of build phases", 2, target.getBuildPhases().size());
    assertHasSingleSourcesPhaseWithSourcesAndFlags(
        target,
        ImmutableMap.of(
            "foo.cpp", Optional.of("-foo"),
            "bar.cpp", Optional.empty()));
  }

  @Test
  public void testAppleLibraryConfiguresOutputPaths() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setHeaderPathPrefix(Optional.of("MyHeaderPathPrefix"))
            .setPrefixHeader(Optional.of(FakeSourcePath.of("Foo/Foo-Prefix.pch")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertThat(target.getProductType(), equalTo(ProductTypes.STATIC_LIBRARY));

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals("../Foo/Foo-Prefix.pch", settings.get("GCC_PREFIX_HEADER"));
    assertEquals(
        "$SYMROOT/$CONFIGURATION$EFFECTIVE_PLATFORM_NAME", settings.get("BUILT_PRODUCTS_DIR"));
    assertEquals("$BUILT_PRODUCTS_DIR", settings.get("CONFIGURATION_BUILD_DIR"));
  }

  @Test
  public void testAppleLibraryConfiguresPrecompiledHeader() throws IOException {
    BuildTarget pchTarget = BuildTargetFactory.newInstance("//foo", "pch");
    TargetNode<?> pchNode =
        CxxPrecompiledHeaderBuilder.createBuilder(pchTarget)
            .setSrc(FakeSourcePath.of("Foo/Foo-Prefix.pch"))
            .build();

    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setPrecompiledHeader(Optional.of(DefaultBuildTargetSourcePath.of(pchTarget)))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, pchNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertThat(target.getProductType(), equalTo(ProductTypes.STATIC_LIBRARY));

    ImmutableMap<String, String> settings = getBuildSettings(libraryTarget, target, "Debug");
    assertEquals("../Foo/Foo-Prefix.pch", settings.get("GCC_PREFIX_HEADER"));
  }

  @Test
  public void testAppleLibraryConfiguresSharedLibraryOutputPaths() throws IOException {
    BuildTarget buildTarget =
        BuildTargetFactory.newInstance("//hi", "lib", CxxDescriptionEnhancer.SHARED_FLAVOR);
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setHeaderPathPrefix(Optional.of("MyHeaderPathPrefix"))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(
            projectGenerator.getGeneratedProject(), "//hi:lib#shared");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertThat(target.getProductType(), equalTo(ProductTypes.DYNAMIC_LIBRARY));

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$SYMROOT/$CONFIGURATION$EFFECTIVE_PLATFORM_NAME", settings.get("BUILT_PRODUCTS_DIR"));
    assertEquals("$BUILT_PRODUCTS_DIR", settings.get("CONFIGURATION_BUILD_DIR"));
  }

  @Test
  public void testAppleLibraryDoesntOverrideHeaderOutputPath() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(
                ImmutableSortedMap.of(
                    "Debug", ImmutableMap.of("PUBLIC_HEADERS_FOLDER_PATH", "FooHeaders")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertThat(target.getProductType(), equalTo(ProductTypes.STATIC_LIBRARY));

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$SYMROOT/$CONFIGURATION$EFFECTIVE_PLATFORM_NAME", settings.get("BUILT_PRODUCTS_DIR"));
    assertEquals("$BUILT_PRODUCTS_DIR", settings.get("CONFIGURATION_BUILD_DIR"));
    assertEquals("FooHeaders", settings.get("PUBLIC_HEADERS_FOLDER_PATH"));
  }

  @Test
  public void testAppleLibraryCxxCFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1'", settings.get("OTHER_CFLAGS"));
    assertEquals(
        "$(inherited) -Wundeclared-selector -Wno-objc-designated-initializers '-DDEBUG=1'",
        settings.get("OTHER_CPLUSPLUSFLAGS"));
  }

  @Test
  public void testAppleLibraryPlatformSpecificCxxFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .build();

    ImmutableSet<Flavor> appleFlavors =
        ImmutableSet.of(
            InternalFlavor.of("appletvos-armv7"), InternalFlavor.of("iphonesimulator-x86_64"));
    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node),
            ImmutableSet.of(node),
            buildTarget,
            ProjectGeneratorOptions.builder().build(),
            appleFlavors,
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion -Wno-nullability-completeness '-DDEBUG=1'",
        settings.get("OTHER_CFLAGS[sdk=appletvos*][arch=armv7]"));
  }

  @Test
  public void testAppleLibraryCompilerAndPreprocessorFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setCompilerFlags(ImmutableList.of("-fhello"))
            .setPreprocessorFlags(ImmutableList.of("-fworld"))
            .setSwiftCompilerFlags(
                StringWithMacrosUtils.fromStrings(ImmutableList.of("-fhello-swift")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1' -fhello -fworld",
        settings.get("OTHER_CFLAGS"));
    assertEquals("$(inherited) -fhello-swift", settings.get("OTHER_SWIFT_FLAGS"));
  }

  @Test
  public void testAppleLibraryCompilerAndPreprocessorFlagsDontPropagate() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setCompilerFlags(ImmutableList.of("-fhello"))
            .setPreprocessorFlags(ImmutableList.of("-fworld"))
            .build();

    BuildTarget dependentBuildTarget = BuildTargetFactory.newInstance("//foo", "bin");
    TargetNode<?> dependentNode =
        AppleBinaryBuilder.createBuilder(dependentBuildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setDeps(ImmutableSortedSet.of(buildTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(node, dependentNode), dependentBuildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:bin");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1'", settings.get("OTHER_CFLAGS"));
  }

  @Test
  public void testAppleLibraryExportedPreprocessorFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setExportedPreprocessorFlags(ImmutableList.of("-DHELLO"))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1' -DHELLO",
        settings.get("OTHER_CFLAGS"));
  }

  @Test
  public void testAppleLibraryExportedPreprocessorFlagsPropagate() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setExportedPreprocessorFlags(ImmutableList.of("-DHELLO"))
            .build();

    BuildTarget dependentBuildTarget = BuildTargetFactory.newInstance("//foo", "bin");
    TargetNode<?> dependentNode =
        AppleBinaryBuilder.createBuilder(dependentBuildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setDeps(ImmutableSortedSet.of(buildTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(node, dependentNode), dependentBuildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:bin");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1' -DHELLO",
        settings.get("OTHER_CFLAGS"));
  }

  @Test
  public void testAppleLibraryLinkerFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setLinkerFlags(
                ImmutableList.of(
                    StringWithMacrosUtils.format("-Xlinker"),
                    StringWithMacrosUtils.format("-lhello")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -fatal_warnings -ObjC -Xlinker -lhello", settings.get("OTHER_LDFLAGS"));
  }

  @Test
  public void testAppleLibraryLinkerFlagsWithLocationMacrosAreExpanded() throws IOException {
    BuildTarget exportFileTarget = BuildTargetFactory.newInstance("//foo", "libExported.a");
    TargetNode<?> exportFileNode =
        new ExportFileBuilder(exportFileTarget).setSrc(FakeSourcePath.of("libExported.a")).build();

    BuildTarget transitiveDepOfGenruleTarget =
        BuildTargetFactory.newInstance("//foo", "libExported2.a");
    TargetNode<?> transitiveDepOfGenruleNode =
        new ExportFileBuilder(transitiveDepOfGenruleTarget)
            .setSrc(FakeSourcePath.of("libExported2.a"))
            .build();

    BuildTarget genruleTarget = BuildTargetFactory.newInstance("//foo", "genrulelib");
    TargetNode<?> genruleNode =
        GenruleBuilder.newGenruleBuilder(genruleTarget)
            .setCmd("cp $(location //foo:libExported2.a) $OUT")
            .setOut("libGenruleLib.a")
            .build();

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setLinkerFlags(
                ImmutableList.of(
                    StringWithMacrosUtils.format("-force_load"),
                    StringWithMacrosUtils.format("%s", LocationMacro.of(genruleTarget)),
                    StringWithMacrosUtils.format("%s", LocationMacro.of(exportFileTarget))))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node, genruleNode, exportFileNode, transitiveDepOfGenruleNode),
            buildTarget);

    ProjectGenerator.Result result = projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    assertThat(
        result.requiredBuildTargets, equalTo(ImmutableSet.of(genruleTarget, exportFileTarget)));

    ImmutableSet<TargetNode<?>> nodes =
        ImmutableSet.of(genruleNode, node, exportFileNode, transitiveDepOfGenruleNode);
    String generatedLibraryPath = getAbsoluteOutputForNode(genruleNode, nodes).toString();
    String exportedLibraryPath = getAbsoluteOutputForNode(exportFileNode, nodes).toString();

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        String.format(
            "$(inherited) -fatal_warnings -ObjC -force_load %s %s",
            generatedLibraryPath, exportedLibraryPath),
        settings.get("OTHER_LDFLAGS"));
  }

  @Test
  public void testAppleLibraryLinkerFlagsDontPropagate() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setLinkerFlags(ImmutableList.of(StringWithMacrosUtils.format("-lhello")))
            .build();

    BuildTarget dependentBuildTarget = BuildTargetFactory.newInstance("//foo", "bin");
    TargetNode<?> dependentNode =
        AppleBinaryBuilder.createBuilder(dependentBuildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setDeps(ImmutableSortedSet.of(buildTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(node, dependentNode), dependentBuildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:bin");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals("$(inherited) -fatal_warnings -ObjC", settings.get("OTHER_LDFLAGS"));
  }

  @Test
  public void testAppleLibraryExportedLinkerFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setExportedLinkerFlags(
                ImmutableList.of(
                    StringWithMacrosUtils.format("-Xlinker"),
                    StringWithMacrosUtils.format("-lhello")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -fatal_warnings -ObjC -Xlinker -lhello", settings.get("OTHER_LDFLAGS"));
  }

  @Test
  public void testAppleLibraryExportedLinkerFlagsPropagate() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setExportedLinkerFlags(
                ImmutableList.of(
                    StringWithMacrosUtils.format("-Xlinker"),
                    StringWithMacrosUtils.format("-lhello")))
            .build();

    BuildTarget dependentBuildTarget = BuildTargetFactory.newInstance("//foo", "bin");
    TargetNode<?> dependentNode =
        AppleBinaryBuilder.createBuilder(dependentBuildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setDeps(ImmutableSortedSet.of(buildTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(node, dependentNode), dependentBuildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:bin");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -fatal_warnings -ObjC -Xlinker -lhello", settings.get("OTHER_LDFLAGS"));
  }

  @Test
  public void testCxxLibraryCompilerAndPreprocessorFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setCompilerFlags(ImmutableList.of("-ffoo"))
            .setPreprocessorFlags(ImmutableList.of("-fbar"))
            .setLinkerFlags(ImmutableList.of(StringWithMacrosUtils.format("-lbaz")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertHasConfigurations(target, "Debug", "Release", "Profile");
    ImmutableMap<String, String> settings =
        ProjectGeneratorTestUtils.getBuildSettings(projectFilesystem, buildTarget, target, "Debug");

    assertEquals(
        "$(inherited) " + "-Wno-deprecated -Wno-conversion '-DDEBUG=1' -ffoo -fbar",
        settings.get("OTHER_CFLAGS"));
    assertEquals(
        "$(inherited) "
            + "-Wundeclared-selector -Wno-objc-designated-initializers '-DDEBUG=1' -ffoo -fbar",
        settings.get("OTHER_CPLUSPLUSFLAGS"));
    assertEquals("$(inherited) -fatal_warnings -ObjC -lbaz", settings.get("OTHER_LDFLAGS"));
  }

  @Test
  public void testCxxLibraryPlatformFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setPlatformCompilerFlags(
                PatternMatchedCollection.<ImmutableList<String>>builder()
                    .add(Pattern.compile("android.*"), ImmutableList.of("-ffoo-android"))
                    .add(Pattern.compile("iphone.*"), ImmutableList.of("-ffoo-iphone"))
                    .add(Pattern.compile("macosx.*"), ImmutableList.of("-ffoo-macosx"))
                    .build())
            .setPlatformPreprocessorFlags(
                PatternMatchedCollection.<ImmutableList<String>>builder()
                    .add(Pattern.compile("android.*"), ImmutableList.of("-fbar-android"))
                    .add(Pattern.compile("iphone.*"), ImmutableList.of("-fbar-iphone"))
                    .add(Pattern.compile("macosx.*"), ImmutableList.of("-fbar-macosx"))
                    .build())
            .setPlatformLinkerFlags(
                PatternMatchedCollection.<ImmutableList<StringWithMacros>>builder()
                    .add(
                        Pattern.compile("android.*"),
                        ImmutableList.of(StringWithMacrosUtils.format("-lbaz-android")))
                    .add(
                        Pattern.compile("iphone.*"),
                        ImmutableList.of(StringWithMacrosUtils.format("-lbaz-iphone")))
                    .add(
                        Pattern.compile("macosx.*"),
                        ImmutableList.of(StringWithMacrosUtils.format("-lbaz-macosx")))
                    .build())
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node),
            ImmutableSet.of(node),
            buildTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(
                InternalFlavor.of("iphonesimulator-x86_64"), InternalFlavor.of("macosx-x86_64")),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertHasConfigurations(target, "Debug", "Release", "Profile");
    ImmutableMap<String, String> settings =
        ProjectGeneratorTestUtils.getBuildSettings(projectFilesystem, buildTarget, target, "Debug");

    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1'", settings.get("OTHER_CFLAGS"));
    assertEquals(
        "$(inherited) " + "-Wundeclared-selector -Wno-objc-designated-initializers '-DDEBUG=1'",
        settings.get("OTHER_CPLUSPLUSFLAGS"));
    assertEquals("$(inherited) -fatal_warnings -ObjC", settings.get("OTHER_LDFLAGS"));

    assertEquals(
        "$(inherited) " + "-Wno-deprecated -Wno-conversion '-DDEBUG=1' -ffoo-iphone -fbar-iphone",
        settings.get("OTHER_CFLAGS[sdk=iphonesimulator*][arch=x86_64]"));
    assertEquals(
        "$(inherited) "
            + "-Wundeclared-selector -Wno-objc-designated-initializers '-DDEBUG=1' -ffoo-iphone -fbar-iphone",
        settings.get("OTHER_CPLUSPLUSFLAGS[sdk=iphonesimulator*][arch=x86_64]"));
    assertEquals(
        "$(inherited) -fatal_warnings -ObjC -lbaz-iphone",
        settings.get("OTHER_LDFLAGS[sdk=iphonesimulator*][arch=x86_64]"));
  }

  @Test
  public void testCxxLibraryExportedPreprocessorFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedPreprocessorFlags(ImmutableList.of("-DHELLO"))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    assertHasConfigurations(target, "Debug", "Release", "Profile");
    ImmutableMap<String, String> settings =
        ProjectGeneratorTestUtils.getBuildSettings(projectFilesystem, buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1' -DHELLO",
        settings.get("OTHER_CFLAGS"));
  }

  @Test
  public void testCxxLibraryExportedPreprocessorFlagsPropagate() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedPreprocessorFlags(ImmutableList.of("-DHELLO"))
            .build();

    BuildTarget dependentBuildTarget = BuildTargetFactory.newInstance("//foo", "bin");
    TargetNode<?> dependentNode =
        AppleBinaryBuilder.createBuilder(dependentBuildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setPreprocessorFlags(ImmutableList.of("-D__APPLE__"))
            .setDeps(ImmutableSortedSet.of(buildTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(node, dependentNode), dependentBuildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:bin");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1' -DHELLO -D__APPLE__",
        settings.get("OTHER_CFLAGS"));
  }

  @Test
  public void testCxxLibraryExportedPlatformFlags() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        new CxxLibraryBuilder(buildTarget)
            .setExportedPlatformPreprocessorFlags(
                PatternMatchedCollection.<ImmutableList<String>>builder()
                    .add(Pattern.compile("iphone.*"), ImmutableList.of("-fbar-iphone"))
                    .build())
            .setExportedPlatformLinkerFlags(
                PatternMatchedCollection.<ImmutableList<StringWithMacros>>builder()
                    .add(
                        Pattern.compile("macosx.*"),
                        ImmutableList.of(StringWithMacrosUtils.format("-lbaz-macosx")))
                    .build())
            .build();

    BuildTarget dependentBuildTarget = BuildTargetFactory.newInstance("//foo", "bin");
    TargetNode<?> dependentNode =
        AppleBinaryBuilder.createBuilder(dependentBuildTarget)
            .setPlatformCompilerFlags(
                PatternMatchedCollection.<ImmutableList<String>>builder()
                    .add(Pattern.compile("iphone.*"), ImmutableList.of("-ffoo-iphone"))
                    .build())
            .setDeps(ImmutableSortedSet.of(buildTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node, dependentNode),
            ImmutableSet.of(node, dependentNode),
            dependentBuildTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(
                InternalFlavor.of("iphonesimulator-x86_64"), InternalFlavor.of("macosx-x86_64")),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertHasConfigurations(target, "Debug", "Release", "Profile");
    ImmutableMap<String, String> settings =
        ProjectGeneratorTestUtils.getBuildSettings(projectFilesystem, buildTarget, target, "Debug");

    assertEquals(
        "$(inherited) -Wno-deprecated -Wno-conversion '-DDEBUG=1' -fbar-iphone",
        settings.get("OTHER_CFLAGS[sdk=iphonesimulator*][arch=x86_64]"));
    assertEquals(
        "$(inherited) "
            + "-Wundeclared-selector -Wno-objc-designated-initializers '-DDEBUG=1' -fbar-iphone",
        settings.get("OTHER_CPLUSPLUSFLAGS[sdk=iphonesimulator*][arch=x86_64]"));
    assertEquals(null, settings.get("OTHER_LDFLAGS[sdk=iphonesimulator*][arch=x86_64]"));

    PBXTarget dependentTarget =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:bin");
    assertHasConfigurations(target, "Debug", "Release", "Profile");
    ImmutableMap<String, String> dependentSettings =
        ProjectGeneratorTestUtils.getBuildSettings(
            projectFilesystem, dependentBuildTarget, dependentTarget, "Debug");

    assertEquals(
        "$(inherited) " + "-Wno-deprecated -Wno-conversion '-DDEBUG=1' -ffoo-iphone -fbar-iphone",
        dependentSettings.get("OTHER_CFLAGS[sdk=iphonesimulator*][arch=x86_64]"));
    assertEquals(
        "$(inherited) "
            + "-Wundeclared-selector -Wno-objc-designated-initializers '-DDEBUG=1' -ffoo-iphone -fbar-iphone",
        dependentSettings.get("OTHER_CPLUSPLUSFLAGS[sdk=iphonesimulator*][arch=x86_64]"));
    assertEquals(null, dependentSettings.get("OTHER_LDFLAGS[sdk=iphonesimulator*][arch=x86_64]"));
  }

  @Test
  public void testConfigurationSerializationWithoutExistingXcconfig() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of("CUSTOM_SETTING", "VALUE")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    assertThat(target.getProductType(), equalTo(ProductTypes.STATIC_LIBRARY));

    assertHasConfigurations(target, "Debug");
    assertKeepsConfigurationsInGenGroup(projectGenerator.getGeneratedProject(), target);
    XCBuildConfiguration configuration =
        target.getBuildConfigurationList().getBuildConfigurationsByName().asMap().get("Debug");
    assertEquals(configuration.getBuildSettings().count(), 0);

    PBXFileReference xcconfigReference = configuration.getBaseConfigurationReference();
    assertEquals(xcconfigReference.getPath(), "../buck-out/gen/foo/lib-Debug.xcconfig");

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$SYMROOT/$CONFIGURATION$EFFECTIVE_PLATFORM_NAME", settings.get("BUILT_PRODUCTS_DIR"));
    assertEquals("$BUILT_PRODUCTS_DIR", settings.get("CONFIGURATION_BUILD_DIR"));
    assertEquals("VALUE", settings.get("CUSTOM_SETTING"));
  }

  @Test
  public void testAppleLibraryDependentsSearchHeadersAndLibraries() throws IOException {
    ImmutableSortedMap<String, ImmutableMap<String, String>> configs =
        ImmutableSortedMap.of("Debug", ImmutableMap.of());

    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setConfigs(configs)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("foo.m"))))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Library.framework"),
                            Optional.empty()))))
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "xctest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(configs)
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("fooTest.m"))))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Test.framework"),
                            Optional.empty()))))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, testNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:xctest");

    ImmutableMap<String, String> settings = getBuildSettings(testTarget, target, "Debug");
    assertEquals(
        "$(inherited) "
            + String.format("%s/buck-out/gen/_p/ptQfVNNRRE-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap", rootPath),
        settings.get("HEADER_SEARCH_PATHS"));
    assertEquals(null, settings.get("USER_HEADER_SEARCH_PATHS"));
    assertEquals("$(inherited) $BUILT_PRODUCTS_DIR", settings.get("LIBRARY_SEARCH_PATHS"));
    assertEquals("$(inherited) $BUILT_PRODUCTS_DIR", settings.get("FRAMEWORK_SEARCH_PATHS"));
  }

  @Test
  public void testAppleLibraryDependentsInheritSearchPaths() throws IOException {
    ImmutableSortedMap<String, ImmutableMap<String, String>> configs =
        ImmutableSortedMap.of(
            "Debug",
            ImmutableMap.of(
                "HEADER_SEARCH_PATHS", "headers",
                "USER_HEADER_SEARCH_PATHS", "user_headers",
                "LIBRARY_SEARCH_PATHS", "libraries",
                "FRAMEWORK_SEARCH_PATHS", "frameworks"));

    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setConfigs(configs)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("foo.m"))))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Library.framework"),
                            Optional.empty()))))
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "xctest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(configs)
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("fooTest.m"))))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Test.framework"),
                            Optional.empty()))))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, testNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:xctest");

    ImmutableMap<String, String> settings = getBuildSettings(testTarget, target, "Debug");
    assertEquals(
        "headers "
            + String.format("%s/buck-out/gen/_p/ptQfVNNRRE-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap", rootPath),
        settings.get("HEADER_SEARCH_PATHS"));
    assertEquals("user_headers", settings.get("USER_HEADER_SEARCH_PATHS"));
    assertEquals("libraries $BUILT_PRODUCTS_DIR", settings.get("LIBRARY_SEARCH_PATHS"));
    assertEquals("frameworks $BUILT_PRODUCTS_DIR", settings.get("FRAMEWORK_SEARCH_PATHS"));
  }

  @Test
  public void testAppleLibraryTransitiveDependentsSearchHeadersAndLibraries() throws IOException {
    ImmutableSortedMap<String, ImmutableMap<String, String>> configs =
        ImmutableSortedMap.of("Debug", ImmutableMap.of());

    BuildTarget libraryDepTarget = BuildTargetFactory.newInstance("//bar", "lib");
    TargetNode<?> libraryDepNode =
        AppleLibraryBuilder.createBuilder(libraryDepTarget)
            .setConfigs(configs)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("foo.m"))))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Library.framework"),
                            Optional.empty()))))
            .build();

    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setConfigs(configs)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("foo.m"))))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Library.framework"),
                            Optional.empty()))))
            .setDeps(ImmutableSortedSet.of(libraryDepTarget))
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "xctest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(configs)
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("fooTest.m"))))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Test.framework"),
                            Optional.empty()))))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(libraryDepNode, libraryNode, testNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:xctest");

    ImmutableMap<String, String> settings = getBuildSettings(testTarget, target, "Debug");
    assertEquals(
        "$(inherited) "
            + String.format("%s/buck-out/gen/_p/ptQfVNNRRE-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap", rootPath),
        settings.get("HEADER_SEARCH_PATHS"));
    assertEquals(null, settings.get("USER_HEADER_SEARCH_PATHS"));
    assertEquals("$(inherited) " + "$BUILT_PRODUCTS_DIR", settings.get("LIBRARY_SEARCH_PATHS"));
    assertEquals("$(inherited) " + "$BUILT_PRODUCTS_DIR", settings.get("FRAMEWORK_SEARCH_PATHS"));
  }

  @Test
  public void testAppleLibraryWithoutSources() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Library.framework"),
                            Optional.empty()))))
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "xctest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("fooTest.m"))))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, testNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:xctest");

    PBXShellScriptBuildPhase shellScriptBuildPhase =
        ProjectGeneratorTestUtils.getSingleBuildPhaseOfType(target, PBXShellScriptBuildPhase.class);

    assertEquals("Buck Build", shellScriptBuildPhase.getName().get());
  }

  @Test
  public void testAppleLibraryWithoutSourcesWithHeaders() throws IOException {
    ImmutableSortedMap<String, ImmutableMap<String, String>> configs =
        ImmutableSortedMap.of(
            "Debug",
            ImmutableMap.of(
                "HEADER_SEARCH_PATHS", "headers",
                "LIBRARY_SEARCH_PATHS", "libraries"));

    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setConfigs(configs)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("HeaderGroup1/bar.h")))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Library.framework"),
                            Optional.empty()))))
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "xctest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(configs)
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("fooTest.m"))))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, testNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:xctest");

    ImmutableMap<String, String> settings = getBuildSettings(testTarget, target, "Debug");
    assertEquals(
        "headers "
            + String.format("%s/buck-out/gen/_p/ptQfVNNRRE-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap", rootPath),
        settings.get("HEADER_SEARCH_PATHS"));
    assertEquals("libraries $BUILT_PRODUCTS_DIR", settings.get("LIBRARY_SEARCH_PATHS"));

    assertEquals("Should have exact number of build phases", 3, target.getBuildPhases().size());
  }

  @Test
  public void testAppleTestRule() throws IOException {
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "xctest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(testNode), testTarget);
    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:xctest");
    assertEquals(target.getProductType(), ProductTypes.UNIT_TEST);
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    PBXFileReference productReference = target.getProductReference();
    assertEquals("xctest.xctest", productReference.getName());
  }

  @Test
  public void testAppleBinaryRule() throws IOException {
    BuildTarget depTarget = BuildTargetFactory.newInstance("//dep", "dep");
    TargetNode<?> depNode =
        AppleLibraryBuilder.createBuilder(depTarget)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("e.m"))))
            .build();

    BuildTarget binaryTarget = BuildTargetFactory.newInstance("//foo", "binary");
    TargetNode<?> binaryNode =
        AppleBinaryBuilder.createBuilder(binaryTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.m"), ImmutableList.of("-foo"))))
            .setExtraXcodeSources(ImmutableList.of(FakeSourcePath.of("libsomething.a")))
            .setHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .setFrameworks(
                ImmutableSortedSet.of(
                    FrameworkPath.ofSourceTreePath(
                        new SourceTreePath(
                            PBXReference.SourceTree.SDKROOT,
                            Paths.get("Foo.framework"),
                            Optional.empty()))))
            .setDeps(ImmutableSortedSet.of(depTarget))
            .setHeaderPathPrefix(Optional.empty())
            .setPrefixHeader(Optional.empty())
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(depNode, binaryNode), binaryTarget);
    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:binary");
    assertHasConfigurations(target, "Debug");
    assertEquals(target.getProductType(), ProductTypes.TOOL);
    assertEquals("Should have exact number of build phases", 2, target.getBuildPhases().size());
    assertHasSingleSourcesPhaseWithSourcesAndFlags(
        target,
        ImmutableMap.of(
            "foo.m", Optional.of("-foo"),
            "libsomething.a", Optional.empty()));

    // this test does not have a dependency on any asset catalogs, so verify no build phase for them
    // exists.
    assertTrue(
        FluentIterable.from(target.getBuildPhases())
            .filter(PBXResourcesBuildPhase.class)
            .isEmpty());
  }

  @Test
  public void testAppleBundleRuleForSharedLibraryFramework() throws IOException {
    BuildTarget sharedLibraryTarget =
        BuildTargetFactory.newInstance("//dep", "shared", CxxDescriptionEnhancer.SHARED_FLAVOR);
    TargetNode<?> sharedLibraryNode =
        AppleLibraryBuilder.createBuilder(sharedLibraryTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .build();

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    TargetNode<?> node =
        AppleBundleBuilder.createBuilder(buildTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(sharedLibraryTarget)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(sharedLibraryNode, node), buildTarget);
    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:bundle");
    assertEquals(target.getProductType(), ProductTypes.FRAMEWORK);
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    PBXFileReference productReference = target.getProductReference();
    assertEquals("bundle.framework", productReference.getName());
    assertEquals(Optional.of("wrapper.framework"), productReference.getExplicitFileType());

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals("framework", settings.get("WRAPPER_EXTENSION"));
  }

  @Test
  public void testAppleResourceWithVariantGroupSetsFileTypeBasedOnPath() throws IOException {
    BuildTarget resourceTarget =
        BuildTargetFactory.newInstance("//foo", "resource", DEFAULT_FLAVOR);
    TargetNode<?> resourceNode =
        AppleResourceBuilder.createBuilder(resourceTarget)
            .setFiles(ImmutableSet.of())
            .setDirs(ImmutableSet.of())
            .setVariants(ImmutableSet.of(FakeSourcePath.of("Base.lproj/Bar.storyboard")))
            .build();
    BuildTarget fooLibraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> fooLibraryNode =
        AppleLibraryBuilder.createBuilder(fooLibraryTarget)
            .setDeps(ImmutableSortedSet.of(resourceTarget))
            .build();
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.BUNDLE))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(fooLibraryTarget)
            .setDeps(ImmutableSortedSet.of(resourceTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(fooLibraryNode, bundleNode, resourceNode), bundleTarget);
    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup storyboardGroup = project.getMainGroup().getOrCreateChildGroupByName("Base.lproj");
    List<PBXReference> storyboardGroupChildren = storyboardGroup.getChildren();
    assertEquals(1, storyboardGroupChildren.size());
    assertTrue(storyboardGroupChildren.get(0) instanceof PBXFileReference);
    PBXFileReference baseStoryboardReference = (PBXFileReference) storyboardGroupChildren.get(0);

    assertEquals("Bar.storyboard", baseStoryboardReference.getName());
  }

  @Test
  public void testAppleBundleRuleWithCustomXcodeProductType() throws IOException {
    BuildTarget sharedLibraryTarget =
        BuildTargetFactory.newInstance("//dep", "shared", CxxDescriptionEnhancer.SHARED_FLAVOR);
    TargetNode<?> sharedLibraryNode =
        AppleLibraryBuilder.createBuilder(sharedLibraryTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .build();

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "custombundle");
    TargetNode<?> node =
        AppleBundleBuilder.createBuilder(buildTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(sharedLibraryTarget)
            .setXcodeProductType(Optional.of("com.facebook.buck.niftyProductType"))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(sharedLibraryNode, node), buildTarget);
    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:custombundle");
    assertEquals(target.getProductType(), ProductType.of("com.facebook.buck.niftyProductType"));
    assertThat(target.isa(), equalTo("PBXNativeTarget"));
    PBXFileReference productReference = target.getProductReference();
    assertEquals("custombundle.framework", productReference.getName());
    assertEquals(Optional.of("wrapper.framework"), productReference.getExplicitFileType());

    ImmutableMap<String, String> settings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals("framework", settings.get("WRAPPER_EXTENSION"));
  }

  @Test
  public void testAppleBundleRuleWithCustomXcodeProductNameFromConfigs() throws IOException {
    BuildTarget sharedLibraryTarget =
        BuildTargetFactory.newInstance("//dep", "shared", CxxDescriptionEnhancer.SHARED_FLAVOR);
    TargetNode<?> sharedLibraryNode =
        AppleLibraryBuilder.createBuilder(sharedLibraryTarget)
            .setConfigs(
                ImmutableSortedMap.of("Debug", ImmutableMap.of("PRODUCT_NAME", "FancyFramework")))
            .build();

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "custombundle");
    TargetNode<?> node =
        AppleBundleBuilder.createBuilder(buildTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.FRAMEWORK))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(sharedLibraryTarget)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(sharedLibraryNode, node), buildTarget);
    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:custombundle");

    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Debug");
    assertThat(buildSettings.get("PRODUCT_NAME"), Matchers.equalTo("FancyFramework"));
  }

  private void testRuleAddsReference(BuildTarget ruleTarget, TargetNode<?> ruleNode, String path)
      throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setDeps(ImmutableSortedSet.of(ruleTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(ruleNode, libraryNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup resourcesGroup = project.getMainGroup().getOrCreateChildGroupByName("Resources");

    assertThat(resourcesGroup.getChildren(), hasSize(1));

    PBXFileReference ruleReference =
        (PBXFileReference) Iterables.get(resourcesGroup.getChildren(), 0);
    assertEquals(path, ruleReference.getName());
  }

  @Test
  public void testUnversionedCoreDataModelCreatesFileReference() throws IOException {
    BuildTarget modelTarget = BuildTargetFactory.newInstance("//foo", "model");
    TargetNode<?> modelNode =
        CoreDataModelBuilder.createBuilder(modelTarget)
            .setPath(FakeSourcePath.of("foo/models/foo.xcdatamodel").getRelativePath())
            .build();
    BuildTarget fooLibraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> fooLibraryNode =
        AppleLibraryBuilder.createBuilder(fooLibraryTarget)
            .setDeps(ImmutableSortedSet.of(modelTarget))
            .build();
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.BUNDLE))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(fooLibraryTarget)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(fooLibraryNode, bundleNode, modelNode), bundleTarget);
    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup modelGroup =
        PBXTestUtils.assertHasSubgroupPathAndReturnLast(
            project.getMainGroup(), ImmutableList.of("foo", "models"));
    PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(modelGroup, "foo.xcdatamodel");
  }

  @Test
  public void testVersionedCoreDataModelCreatesGroupWithCurrentVersionSpecified()
      throws IOException {
    BuildTarget modelTarget = BuildTargetFactory.newInstance("//foo", "model");
    Path dataModelRootPath = Paths.get("foo/models/foo.xcdatamodeld");
    TargetNode<?> modelNode =
        CoreDataModelBuilder.createBuilder(modelTarget)
            .setPath(FakeSourcePath.of(dataModelRootPath).getRelativePath())
            .build();
    BuildTarget fooLibraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> fooLibraryNode =
        AppleLibraryBuilder.createBuilder(fooLibraryTarget)
            .setDeps(ImmutableSortedSet.of(modelTarget))
            .build();
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.BUNDLE))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(fooLibraryTarget)
            .build();

    Path currentVersionPath = dataModelRootPath.resolve(".xccurrentversion");
    fakeProjectFilesystem.writeContentsToPath(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
            + "<plist version=\"1.0\">\n"
            + "<dict>\n"
            + "\t<key>_XCCurrentVersionName</key>\n"
            + "\t<string>foov1.1.0.xcdatamodel</string>\n"
            + "</dict>\n"
            + "</plist>",
        currentVersionPath);

    String currentVersionFileName = "foov1.1.0.xcdatamodel";
    Path versionFilePath = dataModelRootPath.resolve(currentVersionFileName);
    fakeProjectFilesystem.mkdirs(versionFilePath);
    fakeProjectFilesystem.writeContentsToPath("", versionFilePath.resolve("contents"));

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(fooLibraryNode, bundleNode, modelNode), bundleTarget);
    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup modelGroup =
        PBXTestUtils.assertHasSubgroupPathAndReturnLast(
            project.getMainGroup(), ImmutableList.of("foo", "models"));
    Optional<XCVersionGroup> versionGroup =
        modelGroup.getChildren().stream()
            .filter(input -> input.getName().equals("foo.xcdatamodeld"))
            .filter(input -> input.getClass() == XCVersionGroup.class)
            .map(input -> (XCVersionGroup) input)
            .findFirst();
    assert (versionGroup.isPresent());

    assertEquals(versionGroup.get().getCurrentVersion().getName(), currentVersionFileName);
    Optional<PBXFileReference> currentCoreDataFileReference =
        versionGroup.get().getChildren().stream()
            .filter(input -> input.getName().equals(currentVersionFileName))
            .findFirst();
    assertTrue(currentCoreDataFileReference.isPresent());
  }

  @Test
  public void testSceneKitAssetsRuleAddsReference() throws IOException {
    BuildTarget sceneKitTarget = BuildTargetFactory.newInstance("//foo", "scenekitasset");
    TargetNode<?> sceneKitNode =
        SceneKitAssetsBuilder.createBuilder(sceneKitTarget)
            .setPath(FakeSourcePath.of("foo.scnassets").getRelativePath())
            .build();
    BuildTarget fooLibraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> fooLibraryNode =
        AppleLibraryBuilder.createBuilder(fooLibraryTarget)
            .setDeps(ImmutableSortedSet.of(sceneKitTarget))
            .build();
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.BUNDLE))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(fooLibraryTarget)
            .build();

    ProjectGenerator generator =
        createProjectGenerator(
            ImmutableList.of(bundleNode, fooLibraryNode, sceneKitNode), bundleTarget);
    generator.createXcodeProjects();

    PBXProject project = generator.getGeneratedProject();
    PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(project.getMainGroup(), "foo.scnassets");
  }

  @Test
  public void testCodeSignEntitlementsAddsReference() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(libraryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of(("Info.plist")))
            .setInfoPlistSubstitutions(ImmutableMap.of("CODE_SIGN_ENTITLEMENTS", "foo.plist"))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libraryNode, bundleNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    Iterable<String> childNames =
        Iterables.transform(project.getMainGroup().getChildren(), PBXReference::getName);
    assertThat(childNames, hasItem("foo.plist"));
  }

  @Test
  public void testAppleWatchTarget() throws IOException {
    BuildTarget watchExtensionBinaryTarget =
        BuildTargetFactory.newInstance("//foo", "WatchExtensionBinary");
    TargetNode<?> watchExtensionBinaryNode =
        AppleBinaryBuilder.createBuilder(watchExtensionBinaryTarget).build();

    BuildTarget watchExtensionTarget =
        BuildTargetFactory.newInstance("//foo", "WatchExtension", WATCH_OS_FLAVOR);
    TargetNode<?> watchExtensionNode =
        AppleBundleBuilder.createBuilder(watchExtensionTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APPEX))
            .setXcodeProductType(Optional.of(WATCH_EXTENSION_PRODUCT_TYPE))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(watchExtensionBinaryTarget)
            .build();

    BuildTarget watchAppBinaryTarget = BuildTargetFactory.newInstance("//foo", "WatchAppBinary");
    TargetNode<?> watchAppBinaryNode =
        AppleBinaryBuilder.createBuilder(watchAppBinaryTarget).build();

    BuildTarget watchAppTarget =
        BuildTargetFactory.newInstance("//foo", "WatchApp", WATCH_OS_FLAVOR);
    TargetNode<?> watchAppNode =
        AppleBundleBuilder.createBuilder(watchAppTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setXcodeProductType(Optional.of(ProductTypes.WATCH_APPLICATION.getIdentifier()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(watchAppBinaryTarget)
            .setDeps(ImmutableSortedSet.of(watchExtensionTarget))
            .build();

    BuildTarget hostAppBinaryTarget = BuildTargetFactory.newInstance("//foo", "HostAppBinary");
    TargetNode<?> hostAppBinaryNode = AppleBinaryBuilder.createBuilder(hostAppBinaryTarget).build();

    BuildTarget hostAppTarget = BuildTargetFactory.newInstance("//foo", "HostApp");
    TargetNode<?> hostAppNode =
        AppleBundleBuilder.createBuilder(hostAppTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(hostAppBinaryTarget)
            .setDeps(ImmutableSortedSet.of(watchAppTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(
                watchExtensionNode,
                watchExtensionBinaryNode,
                watchAppNode,
                watchAppBinaryNode,
                hostAppNode,
                hostAppBinaryNode),
            hostAppTarget);
    projectGenerator.createXcodeProjects();

    PBXProject generatedProject = projectGenerator.getGeneratedProject();
    PBXTarget generatedHostAppTarget =
        assertTargetExistsAndReturnTarget(generatedProject, "//foo:HostApp");
    // Use the fully qualified name of the watch build targets, since they include a flavor
    PBXTarget generatedWatchAppTarget =
        assertTargetExistsAndReturnTarget(generatedProject, watchAppTarget.getFullyQualifiedName());
    PBXTarget generatedWatchExtensionTarget =
        assertTargetExistsAndReturnTarget(
            generatedProject, watchExtensionTarget.getFullyQualifiedName());
    assertEquals(generatedHostAppTarget.getProductType(), ProductTypes.APPLICATION);
    assertEquals(generatedWatchAppTarget.getProductType(), ProductTypes.WATCH_APPLICATION);
    assertEquals(
        generatedWatchExtensionTarget.getProductType(),
        ProductType.of(WATCH_EXTENSION_PRODUCT_TYPE));

    ProjectGeneratorTestUtils.assertHasDependency(
        generatedProject, generatedHostAppTarget, watchAppTarget.getFullyQualifiedName());
    ProjectGeneratorTestUtils.assertHasDependency(
        generatedProject, generatedWatchAppTarget, watchExtensionTarget.getFullyQualifiedName());
  }

  @Test
  public void ruleToTargetMapContainsPBXTarget() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.m"), ImmutableList.of("-foo")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.m"))))
            .setHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    ProjectGenerator.Result result = projectGenerator.createXcodeProjects();

    assertEquals(
        buildTarget, Iterables.getOnlyElement(result.buildTargetsToGeneratedTargetMap.keySet()));

    PBXTarget target = Iterables.getOnlyElement(result.buildTargetsToGeneratedTargetMap.values());
    assertHasSingleSourcesPhaseWithSourcesAndFlags(
        target,
        ImmutableMap.of(
            "foo.m", Optional.of("-foo"),
            "bar.m", Optional.empty()));
  }

  /**
   * Ensure target map filters out duplicated targets with an explicit and implicit static flavor.
   * Ensure the filtering prefers the main workspace target over other project targets.
   *
   * @throws IOException
   */
  @Test
  public void ruleToTargetMapFiltersDuplicatePBXTarget() throws IOException {
    BuildTarget explicitStaticBuildTarget =
        BuildTargetFactory.newInstance("//foo", "lib", CxxDescriptionEnhancer.STATIC_FLAVOR);
    TargetNode<?> explicitStaticNode =
        AppleLibraryBuilder.createBuilder(explicitStaticBuildTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.m"), ImmutableList.of("-foo")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.m"))))
            .setHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    BuildTarget implicitStaticBuildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> implicitStaticNode =
        AppleLibraryBuilder.createBuilder(implicitStaticBuildTarget)
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.m"), ImmutableList.of("-foo")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.m"))))
            .setHeaders(ImmutableSortedSet.of(FakeSourcePath.of("foo.h")))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(explicitStaticNode, implicitStaticNode), explicitStaticBuildTarget);

    ProjectGenerator.Result result = projectGenerator.createXcodeProjects();

    // `implicitStaticBuildTarget` should be filtered out since it duplicates
    // `explicitStaticBuildTarget`, the workspace target, which takes precedence.
    assertEquals(
        explicitStaticBuildTarget,
        Iterables.getOnlyElement(result.buildTargetsToGeneratedTargetMap.keySet()));

    PBXTarget target = Iterables.getOnlyElement(result.buildTargetsToGeneratedTargetMap.values());
    assertHasSingleSourcesPhaseWithSourcesAndFlags(
        target,
        ImmutableMap.of(
            "foo.m", Optional.of("-foo"),
            "bar.m", Optional.empty()));
  }

  @Test
  public void generatedGidsForTargetsAreStable() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "foo");
    TargetNode<?> node = AppleLibraryBuilder.createBuilder(buildTarget).build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);
    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:foo");
    String expectedGID =
        String.format("%08X%08X%08X", target.isa().hashCode(), target.getName().hashCode(), 0);
    assertEquals(
        "expected GID has correct value (value from which it's derived have not changed)",
        "E66DC04E2245423200000000",
        expectedGID);
    assertEquals("generated GID is same as expected", expectedGID, target.getGlobalID());
  }

  @Test
  public void generatedTargetConfigurationHasRepoRootSet() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "rule");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);
    projectGenerator.createXcodeProjects();

    PBXProject generatedProject = projectGenerator.getGeneratedProject();
    ImmutableMap<String, String> settings =
        getBuildSettings(buildTarget, generatedProject.getTargets().get(0), "Debug");
    assertThat(settings, hasKey("REPO_ROOT"));
    assertEquals(
        projectFilesystem.getRootPath().toAbsolutePath().normalize().toString(),
        settings.get("REPO_ROOT"));
  }

  /**
   * The project configurations should have named entries corresponding to every existing target
   * configuration for targets in the project.
   */
  @Test
  public void generatedProjectConfigurationListIsUnionOfAllTargetConfigurations()
      throws IOException {
    BuildTarget buildTarget1 = BuildTargetFactory.newInstance("//foo", "rule1");
    TargetNode<?> node1 =
        AppleLibraryBuilder.createBuilder(buildTarget1)
            .setConfigs(
                ImmutableSortedMap.of(
                    "Conf1", ImmutableMap.of(),
                    "Conf2", ImmutableMap.of()))
            .build();

    BuildTarget buildTarget2 = BuildTargetFactory.newInstance("//foo", "rule2");
    TargetNode<?> node2 =
        AppleLibraryBuilder.createBuilder(buildTarget2)
            .setConfigs(
                ImmutableSortedMap.of(
                    "Conf2", ImmutableMap.of(),
                    "Conf3", ImmutableMap.of()))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(node1, node2), buildTarget1);
    projectGenerator.createXcodeProjects();

    PBXProject generatedProject = projectGenerator.getGeneratedProject();
    Map<String, XCBuildConfiguration> configurations =
        generatedProject.getBuildConfigurationList().getBuildConfigurationsByName().asMap();
    assertThat(configurations, hasKey("Conf1"));
    assertThat(configurations, hasKey("Conf2"));
    assertThat(configurations, hasKey("Conf3"));
  }

  @Test
  public void nonexistentResourceDirectoryShouldThrow() throws IOException {
    Pair<BuildTarget, ImmutableSet<TargetNode<?>>> result =
        setupSimpleLibraryWithResources(
            ImmutableSet.of(), ImmutableSet.of(FakeSourcePath.of("nonexistent-directory")));

    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        "nonexistent-directory specified in the dirs parameter of //foo:res is not a directory");

    ProjectGenerator projectGenerator =
        createProjectGenerator(result.getSecond(), result.getFirst());
    projectGenerator.createXcodeProjects();
  }

  @Test
  public void nonexistentResourceFileShouldThrow() throws IOException {
    Pair<BuildTarget, ImmutableSet<TargetNode<?>>> result =
        setupSimpleLibraryWithResources(
            ImmutableSet.of(FakeSourcePath.of("nonexistent-file.png")), ImmutableSet.of());

    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        "nonexistent-file.png specified in the files parameter of //foo:res is not a regular file");

    ProjectGenerator projectGenerator =
        createProjectGenerator(result.getSecond(), result.getFirst());
    projectGenerator.createXcodeProjects();
  }

  @Test
  public void usingFileAsResourceDirectoryShouldThrow() throws IOException {
    Pair<BuildTarget, ImmutableSet<TargetNode<?>>> result =
        setupSimpleLibraryWithResources(
            ImmutableSet.of(), ImmutableSet.of(FakeSourcePath.of("bar.png")));

    thrown.expect(HumanReadableException.class);
    thrown.expectMessage("bar.png specified in the dirs parameter of //foo:res is not a directory");

    ProjectGenerator projectGenerator =
        createProjectGenerator(result.getSecond(), result.getFirst());
    projectGenerator.createXcodeProjects();
  }

  @Test
  public void usingDirectoryAsResourceFileShouldThrow() throws IOException {
    Pair<BuildTarget, ImmutableSet<TargetNode<?>>> result =
        setupSimpleLibraryWithResources(
            ImmutableSet.of(FakeSourcePath.of("foodir")), ImmutableSet.of());

    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        "foodir specified in the files parameter of //foo:res is not a regular file");

    ProjectGenerator projectGenerator =
        createProjectGenerator(result.getSecond(), result.getFirst());
    projectGenerator.createXcodeProjects();
  }

  @Test
  public void usingBuildTargetSourcePathInResourceDirsOrFilesDoesNotThrow() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//some:rule");
    SourcePath sourcePath = DefaultBuildTargetSourcePath.of(buildTarget);
    TargetNode<?> generatingTarget = new ExportFileBuilder(buildTarget).build();

    ImmutableSet<TargetNode<?>> nodes =
        FluentIterable.from(
                setupSimpleLibraryWithResources(
                        ImmutableSet.of(sourcePath), ImmutableSet.of(sourcePath))
                    .getSecond())
            .append(generatingTarget)
            .toSet();

    ProjectGenerator projectGenerator = createProjectGenerator(nodes, buildTarget);
    projectGenerator.createXcodeProjects();
  }

  private BuckEventBus getFakeBuckEventBus() {
    return BuckEventBusForTests.newInstance(new IncrementingFakeClock(TimeUnit.SECONDS.toNanos(1)));
  }

  @Test
  public void testResolvingExportFile() throws IOException {
    BuildTarget source1Target = BuildTargetFactory.newInstance("//Vendor", "source1");
    BuildTarget source2Target = BuildTargetFactory.newInstance("//Vendor", "source2");
    BuildTarget source2RefTarget = BuildTargetFactory.newInstance("//Vendor", "source2ref");
    BuildTarget source3Target = BuildTargetFactory.newInstance("//Vendor", "source3");
    BuildTarget headerTarget = BuildTargetFactory.newInstance("//Vendor", "header");
    BuildTarget libTarget = BuildTargetFactory.newInstance("//Libraries", "foo");

    TargetNode<ExportFileDescriptionArg> source1 =
        new ExportFileBuilder(source1Target)
            .setSrc(FakeSourcePath.of(projectFilesystem, "Vendor/sources/source1"))
            .build();

    TargetNode<ExportFileDescriptionArg> source2 =
        new ExportFileBuilder(source2Target)
            .setSrc(FakeSourcePath.of(projectFilesystem, "Vendor/source2"))
            .build();

    TargetNode<ExportFileDescriptionArg> source2Ref =
        new ExportFileBuilder(source2RefTarget)
            .setSrc(DefaultBuildTargetSourcePath.of(source2Target))
            .build();

    TargetNode<ExportFileDescriptionArg> source3 = new ExportFileBuilder(source3Target).build();

    TargetNode<ExportFileDescriptionArg> header = new ExportFileBuilder(headerTarget).build();

    TargetNode<AppleLibraryDescriptionArg> library =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(DefaultBuildTargetSourcePath.of(source1Target)),
                    SourceWithFlags.of(DefaultBuildTargetSourcePath.of(source2RefTarget)),
                    SourceWithFlags.of(DefaultBuildTargetSourcePath.of(source3Target))))
            .setPrefixHeader(Optional.of(DefaultBuildTargetSourcePath.of(headerTarget)))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(source1, source2, source2Ref, source3, header, library), libTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(
            projectGenerator.getGeneratedProject(), libTarget.toString());

    assertHasSingleSourcesPhaseWithSourcesAndFlags(
        target,
        ImmutableMap.of(
            "Vendor/sources/source1", Optional.empty(),
            "Vendor/source2", Optional.empty(),
            "Vendor/source3", Optional.empty()));

    ImmutableMap<String, String> settings = getBuildSettings(libTarget, target, "Debug");
    assertEquals("../Vendor/header", settings.get("GCC_PREFIX_HEADER"));
  }

  @Test
  public void applicationTestUsesHostAppAsTestHostAndBundleLoader() throws IOException {
    BuildTarget hostAppBinaryTarget = BuildTargetFactory.newInstance("//foo", "HostAppBinary");
    TargetNode<?> hostAppBinaryNode = AppleBinaryBuilder.createBuilder(hostAppBinaryTarget).build();

    BuildTarget hostAppTarget = BuildTargetFactory.newInstance("//foo", "HostApp");
    TargetNode<?> hostAppNode =
        AppleBundleBuilder.createBuilder(hostAppTarget)
            .setProductName(Optional.of("TestHostApp"))
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(hostAppBinaryTarget)
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "AppTest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setTestHostApp(Optional.of(hostAppTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(hostAppBinaryNode, hostAppNode, testNode), testTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget testPBXTarget =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:AppTest");
    assertPBXTargetHasDependency(
        projectGenerator.getGeneratedProject(), testPBXTarget, "//foo:HostApp");

    ImmutableMap<String, String> settings = getBuildSettings(testTarget, testPBXTarget, "Debug");
    assertEquals(
        "$(BUNDLE_LOADER_BUNDLE_STYLE_CONDITIONAL_$(CONTENTS_FOLDER_PATH:file:identifier))",
        settings.get("BUNDLE_LOADER"));
    assertEquals("$(BUNDLE_LOADER)", settings.get("TEST_HOST"));
    assertEquals(
        "$BUILT_PRODUCTS_DIR/./TestHostApp.app/Contents/MacOS/TestHostApp",
        settings.get("BUNDLE_LOADER_BUNDLE_STYLE_CONDITIONAL_Contents"));
    assertEquals(
        "$BUILT_PRODUCTS_DIR/./TestHostApp.app/TestHostApp",
        settings.get("BUNDLE_LOADER_BUNDLE_STYLE_CONDITIONAL_AppTest_xctest"));
  }

  @Test
  public void uiTestUsesHostAppAsTarget() throws IOException {
    BuildTarget hostAppBinaryTarget = BuildTargetFactory.newInstance("//foo", "HostAppBinary");
    TargetNode<?> hostAppBinaryNode = AppleBinaryBuilder.createBuilder(hostAppBinaryTarget).build();

    BuildTarget hostAppTarget = BuildTargetFactory.newInstance("//foo", "HostApp");
    TargetNode<?> hostAppNode =
        AppleBundleBuilder.createBuilder(hostAppTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(hostAppBinaryTarget)
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "AppTest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setTestHostApp(Optional.of(hostAppTarget))
            .isUiTest(true)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(hostAppBinaryNode, hostAppNode, testNode), testTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget testPBXTarget =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:AppTest");
    assertEquals(testPBXTarget.getProductType(), ProductTypes.UI_TEST);
    assertPBXTargetHasDependency(
        projectGenerator.getGeneratedProject(), testPBXTarget, "//foo:HostApp");

    ImmutableMap<String, String> settings = getBuildSettings(testTarget, testPBXTarget, "Debug");
    // Check starts with as the remainder depends on the bundle style at build time.
    assertEquals(settings.get("TEST_TARGET_NAME"), "//foo:HostApp");
  }

  @Test
  public void uiTestUsesUiTestTargetAsTargetWithBothUiTestTargetAndTestHostPresent()
      throws IOException {
    BuildTarget hostAppBinaryTarget = BuildTargetFactory.newInstance("//foo", "HostAppBinary");
    TargetNode<?> hostAppBinaryNode = AppleBinaryBuilder.createBuilder(hostAppBinaryTarget).build();

    BuildTarget hostAppTarget = BuildTargetFactory.newInstance("//foo", "HostApp");
    TargetNode<?> hostAppNode =
        AppleBundleBuilder.createBuilder(hostAppTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(hostAppBinaryTarget)
            .build();

    BuildTarget uiTestTarget = BuildTargetFactory.newInstance("//foo", "uiTestTarget");
    TargetNode<?> uiTargetAppNode =
        AppleBundleBuilder.createBuilder(uiTestTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(hostAppBinaryTarget)
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "AppTest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setTestHostApp(Optional.of(hostAppTarget))
            .setUiTestTargetApp(Optional.of(uiTestTarget))
            .isUiTest(true)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(hostAppBinaryNode, hostAppNode, uiTargetAppNode, testNode), testTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget testPBXTarget =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:AppTest");
    assertEquals(testPBXTarget.getProductType(), ProductTypes.UI_TEST);
    assertPBXTargetHasDependency(
        projectGenerator.getGeneratedProject(), testPBXTarget, "//foo:uiTestTarget");
    ImmutableMap<String, String> settings = getBuildSettings(testTarget, testPBXTarget, "Debug");
    // Check starts with as the remainder depends on the bundle style at build time.
    assertEquals(settings.get("TEST_TARGET_NAME"), "//foo:uiTestTarget");
  }

  @Test
  public void uiTestUsesUiTestTargetAsTargetWithOnlyUiTestTarget() throws IOException {
    BuildTarget hostAppBinaryTarget = BuildTargetFactory.newInstance("//foo", "HostAppBinary");
    TargetNode<?> hostAppBinaryNode = AppleBinaryBuilder.createBuilder(hostAppBinaryTarget).build();

    BuildTarget uiTestTarget = BuildTargetFactory.newInstance("//foo", "uiTestTarget");
    TargetNode<?> uiTargetAppNode =
        AppleBundleBuilder.createBuilder(uiTestTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setBinary(hostAppBinaryTarget)
            .build();

    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "AppTest");
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setUiTestTargetApp(Optional.of(uiTestTarget))
            .isUiTest(true)
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(hostAppBinaryNode, uiTargetAppNode, testNode), testTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget testPBXTarget =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:AppTest");
    assertEquals(testPBXTarget.getProductType(), ProductTypes.UI_TEST);
    assertPBXTargetHasDependency(
        projectGenerator.getGeneratedProject(), testPBXTarget, "//foo:uiTestTarget");
    ImmutableMap<String, String> settings = getBuildSettings(testTarget, testPBXTarget, "Debug");
    // Check starts with as the remainder depends on the bundle style at build time.
    assertEquals(settings.get("TEST_TARGET_NAME"), "//foo:uiTestTarget");
  }

  @Test
  public void cxxFlagsPropagatedToConfig() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setLangPreprocessorFlags(
                ImmutableMap.of(
                    CxxSource.Type.C,
                    ImmutableList.of("-std=gnu11"),
                    CxxSource.Type.OBJC,
                    ImmutableList.of("-std=gnu11", "-fobjc-arc"),
                    CxxSource.Type.CXX,
                    ImmutableList.of("-std=c++11", "-stdlib=libc++"),
                    CxxSource.Type.OBJCXX,
                    ImmutableList.of("-std=c++11", "-stdlib=libc++", "-fobjc-arc")))
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo1.m")),
                    SourceWithFlags.of(FakeSourcePath.of("foo2.mm")),
                    SourceWithFlags.of(FakeSourcePath.of("foo3.c")),
                    SourceWithFlags.of(FakeSourcePath.of("foo4.cc"))))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    PBXSourcesBuildPhase sourcesBuildPhase =
        ProjectGeneratorTestUtils.getSingleBuildPhaseOfType(target, PBXSourcesBuildPhase.class);

    ImmutableMap<String, String> expected =
        ImmutableMap.of(
            "foo1.m", "-std=gnu11 -fobjc-arc",
            "foo2.mm", "-std=c++11 -stdlib=libc++ -fobjc-arc",
            "foo3.c", "-std=gnu11",
            "foo4.cc", "-std=c++11 -stdlib=libc++");

    for (PBXBuildFile file : sourcesBuildPhase.getFiles()) {
      String fileName = file.getFileRef().getName();
      NSDictionary buildFileSettings = file.getSettings().get();
      NSString compilerFlags = (NSString) buildFileSettings.get("COMPILER_FLAGS");
      assertNotNull("Build file settings should have COMPILER_FLAGS entry", compilerFlags);
      assertEquals(compilerFlags.toString(), expected.get(fileName));
    }
  }

  @Test
  public void testConfiglessAppleTargetGetsDefaultBuildConfigurations() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("foo.mm"))))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    PBXTarget target =
        assertTargetExistsAndReturnTarget(projectGenerator.getGeneratedProject(), "//foo:lib");

    assertHasConfigurations(target, "Debug", "Release", "Profile");

    ImmutableMap<String, String> debugSettings =
        ProjectGeneratorTestUtils.getBuildSettings(projectFilesystem, buildTarget, target, "Debug");
    assertThat(debugSettings.size(), Matchers.greaterThan(0));

    ImmutableMap<String, String> profileSettings =
        ProjectGeneratorTestUtils.getBuildSettings(
            projectFilesystem, buildTarget, target, "Profile");

    ImmutableMap<String, String> releaseSettings =
        ProjectGeneratorTestUtils.getBuildSettings(
            projectFilesystem, buildTarget, target, "Release");
    assertThat(profileSettings, Matchers.equalTo(releaseSettings));
  }

  @Test
  public void testAssetCatalogsUnderLibrary() throws IOException {
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");
    BuildTarget assetCatalogTarget = BuildTargetFactory.newInstance("//foo", "asset_catalog");

    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setTests(ImmutableSortedSet.of(testTarget))
            .setDeps(ImmutableSortedSet.of(assetCatalogTarget))
            .build();
    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(libraryTarget))
            .build();
    TargetNode<?> assetCatalogNode =
        AppleAssetCatalogBuilder.createBuilder(assetCatalogTarget)
            .setDirs(ImmutableSortedSet.of(FakeSourcePath.of("AssetCatalog.xcassets")))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(libraryNode, testNode, assetCatalogNode),
            ImmutableSet.of(libraryNode, testNode, assetCatalogNode),
            testTarget,
            ProjectGeneratorOptions.builder().setShouldUseShortNamesForTargets(true).build(),
            ImmutableSet.of(),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup mainGroup = project.getMainGroup();

    PBXFileReference assetCatalogFile = (PBXFileReference) mainGroup.getChildren().get(0);
    assertEquals("AssetCatalog.xcassets", assetCatalogFile.getName());
  }

  @Test
  public void testResourcesUnderLibrary() throws IOException {
    BuildTarget fileTarget = BuildTargetFactory.newInstance("//foo", "file");
    BuildTarget resourceTarget = BuildTargetFactory.newInstance("//foo", "res");
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> fileNode = new ExportFileBuilder(fileTarget).build();
    TargetNode<?> resourceNode =
        AppleResourceBuilder.createBuilder(resourceTarget)
            .setDirs(ImmutableSet.of())
            .setFiles(ImmutableSet.of(DefaultBuildTargetSourcePath.of(fileTarget)))
            .build();
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setDeps(ImmutableSortedSet.of(resourceTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(fileNode, resourceNode, libraryNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup mainGroup = project.getMainGroup();

    PBXGroup resourcesGroup = mainGroup.getOrCreateDescendantGroupByPath(ImmutableList.of("foo"));
    PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(resourcesGroup, "file");
  }

  @Test
  public void resourceDirectoriesHaveFolderType() throws IOException {
    BuildTarget directoryTarget = BuildTargetFactory.newInstance("//foo", "dir");
    BuildTarget resourceTarget = BuildTargetFactory.newInstance("//foo", "res");
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> directoryNode = new ExportFileBuilder(directoryTarget).build();
    TargetNode<?> resourceNode =
        AppleResourceBuilder.createBuilder(resourceTarget)
            .setDirs(ImmutableSet.of(DefaultBuildTargetSourcePath.of(directoryTarget)))
            .setFiles(ImmutableSet.of())
            .build();
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setDeps(ImmutableSortedSet.of(resourceTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(directoryNode, resourceNode, libraryNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup mainGroup = project.getMainGroup();

    PBXGroup resourcesGroup = mainGroup.getOrCreateDescendantGroupByPath(ImmutableList.of("foo"));
    PBXFileReference resource =
        PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(resourcesGroup, "dir");
    assertThat(resource.getExplicitFileType(), equalTo(Optional.of("folder")));
  }

  @Test
  public void resourceDirectoriesDontHaveFolderTypeIfTheyCanHaveAMoreSpecificType()
      throws IOException {
    BuildTarget directoryTarget = BuildTargetFactory.newInstance("//foo", "dir.iconset");
    BuildTarget resourceTarget = BuildTargetFactory.newInstance("//foo", "res");
    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "lib");

    TargetNode<?> directoryNode = new ExportFileBuilder(directoryTarget).build();
    TargetNode<?> resourceNode =
        AppleResourceBuilder.createBuilder(resourceTarget)
            .setDirs(ImmutableSet.of(DefaultBuildTargetSourcePath.of(directoryTarget)))
            .setFiles(ImmutableSet.of())
            .build();
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setDeps(ImmutableSortedSet.of(resourceTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(directoryNode, resourceNode, libraryNode), libraryTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup mainGroup = project.getMainGroup();

    PBXGroup resourcesGroup = mainGroup.getOrCreateDescendantGroupByPath(ImmutableList.of("foo"));
    PBXFileReference resource =
        PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(resourcesGroup, "dir.iconset");
    assertThat(resource.getExplicitFileType(), not(equalTo(Optional.of("folder"))));
  }

  @Test
  public void testGeneratedProjectStructureAndSettingsWithBridgingHeader() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of())
            .setBridgingHeader(Optional.of(FakeSourcePath.of("BridgingHeader/header1.h")))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);

    projectGenerator.createXcodeProjects();

    // check if bridging header file existing in the project structure
    PBXProject project = projectGenerator.getGeneratedProject();
    PBXGroup targetGroup = project.getMainGroup().getOrCreateChildGroupByName("BridgingHeader");

    PBXFileReference fileRefBridgingHeader =
        (PBXFileReference) Iterables.get(targetGroup.getChildren(), 0);
    assertEquals("header1.h", fileRefBridgingHeader.getName());

    // check for bridging header build setting
    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:lib");
    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Debug");
    assertEquals(
        "$(SRCROOT)/../BridgingHeader/header1.h", buildSettings.get("SWIFT_OBJC_BRIDGING_HEADER"));
  }

  @Test
  public void testGeneratedProjectSettingForSwiftVersion() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of())
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);
    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:lib");
    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Debug");
    assertThat(buildSettings.get("SWIFT_VERSION"), equalTo("1.23"));
  }

  @Test
  public void testGeneratedProjectSettingForSwiftVersionForAppleLibrary() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of())
            .setSwiftVersion(Optional.of("3.0"))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);
    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:lib");
    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Debug");
    assertThat(buildSettings.get("SWIFT_VERSION"), equalTo("3.0"));
  }

  @Test
  public void testGeneratedProjectSettingForSwiftBuildSettingsForAppleLibrary() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//Foo", "Bar");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setSwiftVersion(Optional.of("3.0"))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);
    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//Foo:Bar");
    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Debug");

    assertThat(buildSettings.get("SWIFT_OBJC_INTERFACE_HEADER_NAME"), equalTo("Bar-Swift.h"));
    assertNotNull(
        "Location of Generated Obj-C Header must be known in advance",
        buildSettings.get("DERIVED_FILE_DIR"));
    assertThat(
        buildSettings.get("SWIFT_INCLUDE_PATHS"), equalTo("$(inherited) $BUILT_PRODUCTS_DIR"));
  }

  @Test
  public void testGeneratedProjectSettingForSwiftBuildSettingsForAppleLibraryWithModuleName()
      throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//Foo", "BarWithSuffix");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setSwiftVersion(Optional.of("3.0"))
            .setModuleName("Bar")
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);
    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//Foo:BarWithSuffix");
    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Debug");

    assertThat(buildSettings.get("SWIFT_OBJC_INTERFACE_HEADER_NAME"), equalTo("Bar-Swift.h"));
    assertThat(buildSettings.get("PRODUCT_MODULE_NAME"), equalTo("Bar"));
  }

  @Test
  public void testGeneratedProjectSettingForSwiftWholeModuleOptimizationForAppleLibrary()
      throws IOException {
    ImmutableMap<String, ImmutableMap<String, String>> sections =
        ImmutableMap.of(
            "swift",
            ImmutableMap.of(
                "version", "3.0",
                "project_wmo", "true"));

    BuckConfig buckConfig = FakeBuckConfig.builder().setSections(sections).build();
    swiftBuckConfig = new SwiftBuckConfig(buckConfig);

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//Foo", "Bar");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setSwiftVersion(Optional.of("3.0"))
            .build();

    ProjectGenerator projectGenerator = createProjectGenerator(ImmutableSet.of(node), buildTarget);
    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//Foo:Bar");
    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, target, "Debug");

    assertThat(buildSettings.get("COMPILER_INDEX_STORE_ENABLE"), equalTo("NO"));
    assertThat(buildSettings.get("SWIFT_WHOLE_MODULE_OPTIMIZATION"), equalTo("YES"));
  }

  @Test
  public void testGeneratedProjectForAppleBinaryWithSwiftSources() throws IOException {
    BuildTarget binBuildTarget = BuildTargetFactory.newInstance("//foo", "bin");
    TargetNode<?> appBinaryNode =
        AppleBinaryBuilder.createBuilder(binBuildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(appBinaryNode), binBuildTarget);
    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:bin");
    ImmutableMap<String, String> buildSettings = getBuildSettings(binBuildTarget, target, "Debug");
    assertThat(
        buildSettings.get("LD_RUNPATH_SEARCH_PATHS[sdk=iphoneos*]"),
        equalTo("$(inherited) /usr/lib/swift @executable_path/Frameworks @loader_path/Frameworks"));
    assertThat(
        buildSettings.get("LD_RUNPATH_SEARCH_PATHS[sdk=iphonesimulator*]"),
        equalTo("$(inherited) /usr/lib/swift @executable_path/Frameworks @loader_path/Frameworks"));
    assertThat(
        buildSettings.get("LD_RUNPATH_SEARCH_PATHS[sdk=macosx*]"),
        equalTo(
            "$(inherited) /usr/lib/swift @executable_path/../Frameworks @loader_path/../Frameworks"));
  }

  @Test
  public void testGeneratedProjectForAppleBinaryUsingAppleLibraryWithSwiftSources()
      throws IOException {
    BuildTarget libBuildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libBuildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setSwiftVersion(Optional.of("3.0"))
            .build();

    BuildTarget binBuildTarget = BuildTargetFactory.newInstance("//foo", "bin");
    TargetNode<?> binNode =
        AppleBinaryBuilder.createBuilder(binBuildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setDeps(ImmutableSortedSet.of(libBuildTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libNode, binNode), binBuildTarget);
    projectGenerator.createXcodeProjects();
    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget target = assertTargetExistsAndReturnTarget(project, "//foo:bin");
    ImmutableMap<String, String> buildSettings = getBuildSettings(binBuildTarget, target, "Debug");
    assertThat(
        buildSettings.get("LIBRARY_SEARCH_PATHS"),
        containsString("$DT_TOOLCHAIN_DIR/usr/lib/swift/$PLATFORM_NAME"));
    assertThat(
        buildSettings.get("LD_RUNPATH_SEARCH_PATHS[sdk=iphoneos*]"),
        equalTo("$(inherited) /usr/lib/swift @executable_path/Frameworks @loader_path/Frameworks"));
    assertThat(
        buildSettings.get("LD_RUNPATH_SEARCH_PATHS[sdk=iphonesimulator*]"),
        equalTo("$(inherited) /usr/lib/swift @executable_path/Frameworks @loader_path/Frameworks"));
    assertThat(
        buildSettings.get("LD_RUNPATH_SEARCH_PATHS[sdk=macosx*]"),
        equalTo(
            "$(inherited) /usr/lib/swift @executable_path/../Frameworks @loader_path/../Frameworks"));
  }

  @Test
  public void testSwiftObjCGenerateHeaderInHeaderMap() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo", "lib");
    TargetNode<?> node =
        AppleLibraryBuilder.createBuilder(buildTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setSwiftVersion(Optional.of("3.0"))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(node),
            ImmutableSet.of(node),
            buildTarget,
            ProjectGeneratorOptions.builder().build(),
            ImmutableSet.of(),
            Optional.empty());

    projectGenerator.createXcodeProjects();

    HeaderMap headerMap = getHeaderMapInDir(PUBLIC_HEADER_MAP_PATH);
    assertThat(headerMap.getNumEntries(), equalTo(1));

    String objCGeneratedHeaderName = "lib-Swift.h";
    String derivedSourcesUserDir =
        "buck-out/xcode/derived-sources/lib-0b091b4cd38199b85fed37557a12c08fbbca9b32";
    String objCGeneratedHeaderPathName = headerMap.lookup("lib/lib-Swift.h");
    assertTrue(
        objCGeneratedHeaderPathName.endsWith(
            derivedSourcesUserDir + "/" + objCGeneratedHeaderName));

    PBXProject pbxProject = projectGenerator.getGeneratedProject();
    PBXTarget pbxTarget = assertTargetExistsAndReturnTarget(pbxProject, "//foo:lib");
    ImmutableMap<String, String> buildSettings = getBuildSettings(buildTarget, pbxTarget, "Debug");

    assertThat(buildSettings.get("DERIVED_FILE_DIR"), startsWith("/"));
    assertTrue(buildSettings.get("DERIVED_FILE_DIR").endsWith(derivedSourcesUserDir));
    assertThat(
        buildSettings.get("SWIFT_OBJC_INTERFACE_HEADER_NAME"), equalTo(objCGeneratedHeaderName));
  }

  @Test
  public void testSwiftRuntimeIsEmbeddedInBinary() throws IOException {
    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget binaryTarget = BuildTargetFactory.newInstance("//foo", "bin");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");
    BuildTarget testLibTarget = BuildTargetFactory.newInstance("//foo", "testlib");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setSwiftVersion(Optional.of("3.0"))
            .build();

    TargetNode<?> testLibNode =
        AppleLibraryBuilder.createBuilder(testLibTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Bar.swift"))))
            .setSwiftVersion(Optional.of("3.0"))
            .build();

    TargetNode<?> binaryNode =
        AppleBinaryBuilder.createBuilder(binaryTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.h"), ImmutableList.of("public")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.h"))))
            .setDeps(ImmutableSortedSet.of(libTarget))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(binaryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setTests(ImmutableSortedSet.of(testTarget))
            .build();

    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(bundleTarget, testLibTarget))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(
            ImmutableSet.of(libNode, binaryNode, bundleNode, testNode, testLibNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget testPBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:test");
    ImmutableMap<String, String> testBuildSettings =
        getBuildSettings(testTarget, testPBXTarget, "Debug");

    PBXTarget bundlePBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:bundle");
    ImmutableMap<String, String> bundleBuildSettings =
        getBuildSettings(bundleTarget, bundlePBXTarget, "Debug");

    assertThat(testBuildSettings.get("ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES"), equalTo("YES"));
    assertThat(bundleBuildSettings.get("ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES"), equalTo("YES"));
  }

  @Test
  public void testSwiftAddASTPathsLinkerFlags() throws IOException {
    ImmutableMap<String, ImmutableMap<String, String>> sections =
        ImmutableMap.of(
            "swift",
            ImmutableMap.of(
                "version", "3.0",
                "project_add_ast_paths", "true"));

    BuckConfig buckConfig = FakeBuckConfig.builder().setSections(sections).build();
    swiftBuckConfig = new SwiftBuckConfig(buckConfig);

    BuildTarget libTarget = BuildTargetFactory.newInstance("//foo", "lib");
    BuildTarget binaryTarget = BuildTargetFactory.newInstance("//foo", "bin");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");

    TargetNode<?> libNode =
        AppleLibraryBuilder.createBuilder(libTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(FakeSourcePath.of("Foo.swift"))))
            .setSwiftVersion(Optional.of("3.0"))
            .build();

    TargetNode<?> binaryNode =
        AppleBinaryBuilder.createBuilder(binaryTarget)
            .setConfigs(ImmutableSortedMap.of("Debug", ImmutableMap.of()))
            .setSrcs(
                ImmutableSortedSet.of(
                    SourceWithFlags.of(FakeSourcePath.of("foo.h"), ImmutableList.of("public")),
                    SourceWithFlags.of(FakeSourcePath.of("bar.h"))))
            .setDeps(ImmutableSortedSet.of(libTarget))
            .build();

    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(binaryTarget)
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(libNode, binaryNode, bundleNode), bundleTarget);

    projectGenerator.createXcodeProjects();

    PBXProject project = projectGenerator.getGeneratedProject();

    PBXTarget bundlePBXTarget = assertTargetExistsAndReturnTarget(project, "//foo:bundle");
    ImmutableMap<String, String> bundleBuildSettings =
        getBuildSettings(bundleTarget, bundlePBXTarget, "Debug");

    assertThat(
        bundleBuildSettings.get("OTHER_LDFLAGS"),
        containsString(
            "-Xlinker -add_ast_path -Xlinker '${BUILT_PRODUCTS_DIR}/lib.swiftmodule/${CURRENT_ARCH}.swiftmodule'"));
  }

  @Test
  public void testMergedHeaderMap() throws IOException {
    BuildTarget lib1Target = BuildTargetFactory.newInstance("//foo", "lib1");
    BuildTarget lib2Target = BuildTargetFactory.newInstance("//bar", "lib2");
    BuildTarget lib3Target = BuildTargetFactory.newInstance("//foo", "lib3");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");
    BuildTarget lib4Target = BuildTargetFactory.newInstance("//foo", "lib4");

    TargetNode<?> lib1Node =
        AppleLibraryBuilder.createBuilder(lib1Target)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib1.h")))
            .setDeps(ImmutableSortedSet.of(lib2Target))
            .setTests(ImmutableSortedSet.of(testTarget))
            .build();

    TargetNode<?> lib2Node =
        AppleLibraryBuilder.createBuilder(lib2Target)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib2.h")))
            .build();

    TargetNode<?> lib3Node =
        AppleLibraryBuilder.createBuilder(lib3Target)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib3.h")))
            .build();

    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(lib1Target, lib4Target))
            .build();

    TargetNode<?> lib4Node =
        AppleLibraryBuilder.createBuilder(lib4Target)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib4.h")))
            .build();

    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            ImmutableSet.of(lib1Node, lib2Node, lib3Node, testNode, lib4Node));
    AppleDependenciesCache cache = new AppleDependenciesCache(targetGraph);
    ProjectGenerationStateCache projStateCache = new ProjectGenerationStateCache();

    XCodeDescriptions xcodeDescriptions =
        XCodeDescriptionsFactory.create(BuckPluginManagerFactory.createPluginManager());

    // The merged header map should not generated at this point.
    Path hmapPath = Paths.get("buck-out/gen/_p/pub-hmap/.hmap");
    assertFalse(hmapPath + " should NOT exist.", projectFilesystem.isFile(hmapPath));
    // Checks the content of the header search paths.

    ProjectGenerator projectGeneratorLib1 =
        new ProjectGenerator(
            xcodeDescriptions,
            targetGraph,
            cache,
            projStateCache,
            ImmutableSet.of(lib1Target, testTarget), /* lib3Target not included on purpose */
            projectCell,
            "BUCK",
            XcodeProjectWriteOptions.of(new PBXProject(PROJECT_NAME), OUTPUT_DIRECTORY),
            ProjectGeneratorOptions.builder().build(),
            TestRuleKeyConfigurationFactory.create(),
            lib1Target,
            ImmutableSet.of(lib1Target, lib4Target),
            DEFAULT_PLATFORM,
            ImmutableSet.of(),
            getActionGraphBuilderNodeFunction(targetGraph),
            getFakeBuckEventBus(),
            halideBuckConfig,
            cxxBuckConfig,
            appleConfig,
            swiftBuckConfig,
            Optional.empty());

    projectGeneratorLib1.createXcodeProjects();

    // The merged header map should not generated at this point.
    assertTrue(hmapPath + " should exist.", projectFilesystem.isFile(hmapPath));
    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.of(
            "lib1/lib1.h",
            absolutePathForHeader("lib1.h"),
            "lib2/lib2.h",
            absolutePathForHeader("lib2.h"),
            "lib4/lib4.h",
            absolutePathForHeader("lib4.h")));
    // Checks the content of the header search paths.
    PBXProject project1 = projectGeneratorLib1.getGeneratedProject();

    // For //foo:lib1
    PBXTarget testPBXTarget1 = assertTargetExistsAndReturnTarget(project1, "//foo:lib1");

    ImmutableMap<String, String> buildSettings1 =
        getBuildSettings(lib1Target, testPBXTarget1, "Default");

    assertEquals(
        "test binary should use header symlink trees for both public and non-public headers "
            + "of the tested library in HEADER_SEARCH_PATHS",
        "$(inherited) "
            + String.format("%s/buck-out/gen/_p/WNl0jZWMBk-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap", rootPath),
        buildSettings1.get("HEADER_SEARCH_PATHS"));

    // For //foo:test
    PBXTarget testPBXTargetTest = assertTargetExistsAndReturnTarget(project1, "//foo:test");

    ImmutableMap<String, String> buildSettingsTest =
        getBuildSettings(testTarget, testPBXTargetTest, "Default");

    assertEquals(
        "test binary should use header symlink trees for both public and non-public headers "
            + "of the tested library in HEADER_SEARCH_PATHS",
        "$(inherited) "
            + String.format("%s/buck-out/gen/_p/LpygK8zq5F-priv/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/pub-hmap/.hmap ", rootPath)
            + String.format("%s/buck-out/gen/_p/WNl0jZWMBk-priv/.hmap", rootPath),
        buildSettingsTest.get("HEADER_SEARCH_PATHS"));
  }

  @Test
  public void testMergedHeaderMapAbsoluteHeaderMap() throws IOException {
    BuildTarget lib1Target = BuildTargetFactory.newInstance("//foo", "lib1");
    BuildTarget lib2Target = BuildTargetFactory.newInstance("//bar", "lib2");
    BuildTarget lib3Target = BuildTargetFactory.newInstance("//foo", "lib3");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//foo", "test");
    BuildTarget lib4Target = BuildTargetFactory.newInstance("//foo", "lib4");

    TargetNode<?> lib1Node =
        AppleLibraryBuilder.createBuilder(lib1Target)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib1.h")))
            .setDeps(ImmutableSortedSet.of(lib2Target))
            .setTests(ImmutableSortedSet.of(testTarget))
            .build();

    TargetNode<?> lib2Node =
        AppleLibraryBuilder.createBuilder(lib2Target)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib2.h")))
            .build();

    TargetNode<?> lib3Node =
        AppleLibraryBuilder.createBuilder(lib3Target)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib3.h")))
            .build();

    TargetNode<?> testNode =
        AppleTestBuilder.createBuilder(testTarget)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setInfoPlist(FakeSourcePath.of("Info.plist"))
            .setDeps(ImmutableSortedSet.of(lib1Target, lib4Target))
            .build();

    TargetNode<?> lib4Node =
        AppleLibraryBuilder.createBuilder(lib4Target)
            .setConfigs(ImmutableSortedMap.of("Default", ImmutableMap.of()))
            .setExportedHeaders(ImmutableSortedSet.of(FakeSourcePath.of("lib4.h")))
            .build();

    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            ImmutableSet.of(lib1Node, lib2Node, lib3Node, testNode, lib4Node));
    AppleDependenciesCache cache = new AppleDependenciesCache(targetGraph);
    ProjectGenerationStateCache projStateCache = new ProjectGenerationStateCache();

    XCodeDescriptions xcodeDescriptions =
        XCodeDescriptionsFactory.create(BuckPluginManagerFactory.createPluginManager());

    // The merged header map should not generated at this point.
    Path hmapPath = Paths.get("buck-out/gen/_p/pub-hmap/.hmap");
    assertFalse(hmapPath + " should NOT exist.", projectFilesystem.isFile(hmapPath));
    // Checks the content of the header search paths.

    Path currentDirectory = Paths.get(".").toAbsolutePath();

    ProjectGenerator projectGeneratorLib1 =
        new ProjectGenerator(
            xcodeDescriptions,
            targetGraph,
            cache,
            projStateCache,
            ImmutableSet.of(lib1Target, testTarget), /* lib3Target not included on purpose */
            projectCell,
            "BUCK",
            XcodeProjectWriteOptions.of(new PBXProject(PROJECT_NAME), OUTPUT_DIRECTORY),
            ProjectGeneratorOptions.builder().build(),
            TestRuleKeyConfigurationFactory.create(),
            lib1Target,
            ImmutableSet.of(lib1Target, lib4Target),
            DEFAULT_PLATFORM,
            ImmutableSet.of(),
            getActionGraphBuilderNodeFunction(targetGraph),
            getFakeBuckEventBus(),
            halideBuckConfig,
            cxxBuckConfig,
            appleConfig,
            swiftBuckConfig,
            Optional.empty());

    projectGeneratorLib1.createXcodeProjects();

    // The merged header map should not generated at this point.
    assertTrue(hmapPath + " should exist.", projectFilesystem.isFile(hmapPath));
    assertThatHeaderMapWithoutSymLinksContains(
        PUBLIC_HEADER_MAP_PATH,
        ImmutableMap.of(
            "lib1/lib1.h",
            absolutePathForHeader("lib1.h"),
            "lib2/lib2.h",
            absolutePathForHeader("lib2.h"),
            "lib4/lib4.h",
            absolutePathForHeader("lib4.h")));
    // Checks the content of the header search paths.
    PBXProject project1 = projectGeneratorLib1.getGeneratedProject();

    // For //foo:lib1
    PBXTarget testPBXTarget1 = assertTargetExistsAndReturnTarget(project1, "//foo:lib1");

    ImmutableMap<String, String> buildSettings1 =
        getBuildSettings(lib1Target, testPBXTarget1, "Default");

    assertEquals(
        "test binary should use header symlink trees with absolute paths for both public and non-public headers "
            + "of the tested library in HEADER_SEARCH_PATHS",
        "$(inherited) "
            + currentDirectory
                .resolve("buck-out/gen/_p/WNl0jZWMBk-priv/.hmap")
                .normalize()
                .toString()
            + " "
            + currentDirectory.resolve("buck-out/gen/_p/pub-hmap/.hmap").normalize().toString(),
        buildSettings1.get("HEADER_SEARCH_PATHS"));

    // For //foo:test
    PBXTarget testPBXTargetTest = assertTargetExistsAndReturnTarget(project1, "//foo:test");

    ImmutableMap<String, String> buildSettingsTest =
        getBuildSettings(testTarget, testPBXTargetTest, "Default");

    assertEquals(
        "test binary should use header symlink trees with absolute paths for both public and non-public headers "
            + "of the tested library in HEADER_SEARCH_PATHS",
        "$(inherited) "
            + currentDirectory
                .resolve("buck-out/gen/_p/LpygK8zq5F-priv/.hmap")
                .normalize()
                .toString()
            + " "
            + currentDirectory.resolve("buck-out/gen/_p/pub-hmap/.hmap").normalize().toString()
            + " "
            + currentDirectory
                .resolve("buck-out/gen/_p/WNl0jZWMBk-priv/.hmap")
                .normalize()
                .toString(),
        buildSettingsTest.get("HEADER_SEARCH_PATHS"));
  }

  @Test
  public void testEntitlementsPlistAddedToPath() throws IOException {
    BuildTarget binaryTarget = BuildTargetFactory.newInstance("//foo", "bin");
    BuildTarget bundleTarget = BuildTargetFactory.newInstance("//foo", "bundle");

    TargetNode<?> binaryNode = AppleBinaryBuilder.createBuilder(binaryTarget).build();
    TargetNode<?> bundleNode =
        AppleBundleBuilder.createBuilder(bundleTarget)
            .setBinary(binaryTarget)
            .setInfoPlist(FakeSourcePath.of(("Info.plist")))
            .setInfoPlistSubstitutions(
                ImmutableMap.of(
                    AppleBundle.CODE_SIGN_ENTITLEMENTS,
                    "$(SOURCE_ROOT)/Support/Entitlements/My-Entitlements.plist"))
            .setExtension(Either.ofLeft(AppleBundleExtension.APP))
            .build();

    ProjectGenerator projectGenerator =
        createProjectGenerator(ImmutableSet.of(binaryNode, bundleNode), bundleTarget);
    projectGenerator.createXcodeProjects();

    PBXProject generatedProject = projectGenerator.getGeneratedProject();
    PBXGroup entitlementsGroup =
        PBXTestUtils.assertHasSubgroupPathAndReturnLast(
            generatedProject.getMainGroup(), ImmutableList.of("foo", "Support", "Entitlements"));
    PBXTestUtils.assertHasFileReferenceWithNameAndReturnIt(
        entitlementsGroup, "My-Entitlements.plist");
  }

  private ProjectGenerator createProjectGenerator(
      Collection<TargetNode<?>> allNodes, BuildTarget workspaceTarget) {
    return createProjectGenerator(
        allNodes,
        allNodes,
        workspaceTarget,
        ProjectGeneratorOptions.builder().build(),
        ImmutableSet.of(),
        Optional.empty());
  }

  private ProjectGenerator createProjectGenerator(
      Collection<TargetNode<?>> allNodes,
      Collection<TargetNode<?>> initialTargetNodes,
      BuildTarget workspaceTarget,
      ProjectGeneratorOptions projectGeneratorOptions,
      ImmutableSet<Flavor> appleCxxFlavors,
      Optional<ImmutableMap<BuildTarget, TargetNode<?>>> sharedLibrariesToBundles) {
    XCodeDescriptions xcodeDescriptions =
        XCodeDescriptionsFactory.create(BuckPluginManagerFactory.createPluginManager());

    // The graph should contain all nodes
    TargetGraph targetGraph = TargetGraphFactory.newInstance(ImmutableSet.copyOf(allNodes));

    // Initial targets are all targets that are "focused" on
    ImmutableSet<BuildTarget> initialBuildTargets =
        initialTargetNodes.stream()
            .map(TargetNode::getBuildTarget)
            .collect(ImmutableSet.toImmutableSet());

    // Here we assume that all initial targets and workspace targets are ones that we want. Not
    // necessarily all nodes in the graph.
    ImmutableSet<BuildTarget> targetsInRequiredProjects =
        new ImmutableSet.Builder<BuildTarget>()
            .addAll(initialBuildTargets)
            .add(workspaceTarget)
            .build();

    Function<? super TargetNode<?>, ActionGraphBuilder> actionGraphBuilderForNode =
        getActionGraphBuilderNodeFunction(targetGraph);

    AppleDependenciesCache cache = new AppleDependenciesCache(targetGraph);
    ProjectGenerationStateCache projStateCache = new ProjectGenerationStateCache();
    return new ProjectGenerator(
        xcodeDescriptions,
        targetGraph,
        cache,
        projStateCache,
        initialBuildTargets,
        projectCell,
        "BUCK",
        XcodeProjectWriteOptions.of(new PBXProject(PROJECT_NAME), OUTPUT_DIRECTORY),
        projectGeneratorOptions,
        TestRuleKeyConfigurationFactory.create(),
        workspaceTarget,
        targetsInRequiredProjects,
        DEFAULT_PLATFORM,
        appleCxxFlavors,
        actionGraphBuilderForNode,
        getFakeBuckEventBus(),
        halideBuckConfig,
        cxxBuckConfig,
        appleConfig,
        swiftBuckConfig,
        sharedLibrariesToBundles);
  }

  private Function<TargetNode<?>, ActionGraphBuilder> getActionGraphBuilderNodeFunction(
      TargetGraph targetGraph) {
    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder(targetGraph);
    AbstractBottomUpTraversal<TargetNode<?>, RuntimeException> bottomUpTraversal =
        new AbstractBottomUpTraversal<TargetNode<?>, RuntimeException>(targetGraph) {
          @Override
          @SuppressWarnings("PMD.EmptyCatchBlock")
          public void visit(TargetNode<?> node) {
            try {
              graphBuilder.requireRule(node.getBuildTarget());
            } catch (Exception e) {
              // NOTE(agallagher): A large number of the tests appear to setup their target nodes
              // incorrectly, causing action graph creation to fail with lots of missing expected
              // Apple C/C++ platform flavors.  This is gross, but to support tests that need a
              // complete sub-action graph, just skip over the errors.
            }
          }
        };
    bottomUpTraversal.traverse();
    return input -> graphBuilder;
  }

  private Pair<BuildTarget, ImmutableSet<TargetNode<?>>> setupSimpleLibraryWithResources(
      ImmutableSet<SourcePath> resourceFiles, ImmutableSet<SourcePath> resourceDirectories) {
    BuildTarget resourceTarget = BuildTargetFactory.newInstance("//foo", "res");
    TargetNode<?> resourceNode =
        AppleResourceBuilder.createBuilder(resourceTarget)
            .setFiles(resourceFiles)
            .setDirs(resourceDirectories)
            .build();

    BuildTarget libraryTarget = BuildTargetFactory.newInstance("//foo", "foo");
    TargetNode<?> libraryNode =
        AppleLibraryBuilder.createBuilder(libraryTarget)
            .setDeps(ImmutableSortedSet.of(resourceTarget))
            .build();

    return new Pair(libraryTarget, ImmutableSet.of(resourceNode, libraryNode));
  }

  private String assertFileRefIsRelativeAndResolvePath(PBXReference fileRef) {
    assert (!fileRef.getPath().startsWith("/"));
    assertEquals(
        "file path should be relative to project directory",
        PBXReference.SourceTree.SOURCE_ROOT,
        fileRef.getSourceTree());
    return projectFilesystem
        .resolve(OUTPUT_DIRECTORY)
        .resolve(fileRef.getPath())
        .normalize()
        .toString();
  }

  private void assertHasConfigurations(PBXTarget target, String... names) {
    Map<String, XCBuildConfiguration> buildConfigurationMap =
        target.getBuildConfigurationList().getBuildConfigurationsByName().asMap();
    HashSet<String> configs = new HashSet<String>();
    configs.add(BuildConfiguration.DEBUG_BUILD_CONFIGURATION_NAME);
    configs.add(BuildConfiguration.PROFILE_BUILD_CONFIGURATION_NAME);
    configs.add(BuildConfiguration.RELEASE_BUILD_CONFIGURATION_NAME);
    for (String config : names) {
      configs.add(config);
    }
    assertEquals(
        "Configuration list has expected number of entries",
        configs.size(),
        buildConfigurationMap.size());

    for (String name : configs) {
      XCBuildConfiguration configuration = buildConfigurationMap.get(name);

      assertNotNull("Configuration entry exists", configuration);
      assertEquals("Configuration name is same as key", name, configuration.getName());
      assertTrue(
          "Configuration has xcconfig file",
          configuration.getBaseConfigurationReference().getPath().endsWith(".xcconfig"));
    }
  }

  private void assertKeepsConfigurationsInGenGroup(PBXProject project, PBXTarget target) {
    Map<String, XCBuildConfiguration> buildConfigurationMap =
        target.getBuildConfigurationList().getBuildConfigurationsByName().asMap();

    PBXGroup buckOutGroup =
        PBXTestUtils.assertHasSubgroupAndReturnIt(project.getMainGroup(), "buck-out");
    PBXGroup genGroup = PBXTestUtils.assertHasSubgroupAndReturnIt(buckOutGroup, "gen");

    BuildTargetPattern buildTargetPattern =
        BuildTargetPatternParser.parse(target.getName(), projectCell.getCellNameResolver());
    PBXGroup configsGroup = genGroup;
    // File should be located in the buck-out/gen/{target-path}, so iterate through the path
    // components
    // to find the configs directory.
    for (Path component :
        buildTargetPattern
            .getCellRelativeBasePath()
            .getPath()
            .toPath(fakeProjectFilesystem.getFileSystem())) {
      configsGroup = PBXTestUtils.assertHasSubgroupAndReturnIt(configsGroup, component.toString());
    }

    List<PBXReference> configReferences = configsGroup.getChildren();
    assertFalse("Configuration file references exist", configReferences.isEmpty());

    for (XCBuildConfiguration configuration : buildConfigurationMap.values()) {
      String path = configuration.getBaseConfigurationReference().getPath();

      PBXReference foundReference = null;
      for (PBXReference reference : configReferences) {
        assertTrue(
            "References in the configuration group should point to xcconfigs",
            reference.getPath().endsWith(".xcconfig"));

        if (reference.getPath().equals(path)) {
          foundReference = reference;
          break;
        }
      }

      assertNotNull(
          "File reference for configuration " + path + " should be in main group", foundReference);
    }
  }

  private void assertHasSingleSourcesPhaseWithSourcesAndFlags(
      PBXTarget target, ImmutableMap<String, Optional<String>> sourcesAndFlags) {

    PBXSourcesBuildPhase sourcesBuildPhase =
        ProjectGeneratorTestUtils.getSingleBuildPhaseOfType(target, PBXSourcesBuildPhase.class);

    assertEquals(
        "Sources build phase should have correct number of sources",
        sourcesAndFlags.size(),
        sourcesBuildPhase.getFiles().size());

    // map keys to absolute paths
    ImmutableMap.Builder<String, Optional<String>> absolutePathFlagMapBuilder =
        ImmutableMap.builder();
    for (Map.Entry<String, Optional<String>> name : sourcesAndFlags.entrySet()) {
      absolutePathFlagMapBuilder.put(
          projectFilesystem
              .getRootPath()
              .resolve(name.getKey())
              .toAbsolutePath()
              .normalize()
              .toString(),
          name.getValue());
    }
    ImmutableMap<String, Optional<String>> absolutePathFlagMap = absolutePathFlagMapBuilder.build();

    for (PBXBuildFile file : sourcesBuildPhase.getFiles()) {
      String filePath = assertFileRefIsRelativeAndResolvePath(file.getFileRef());
      Optional<String> flags = absolutePathFlagMap.get(filePath);
      assertNotNull(String.format("Unexpected file ref '%s' found", filePath), flags);
      if (flags.isPresent()) {
        assertTrue("Build file should have settings dictionary", file.getSettings().isPresent());

        NSDictionary buildFileSettings = file.getSettings().get();
        NSString compilerFlags = (NSString) buildFileSettings.get("COMPILER_FLAGS");

        assertNotNull("Build file settings should have COMPILER_FLAGS entry", compilerFlags);
        assertEquals(
            "Build file settings should be expected value",
            flags.get(),
            compilerFlags.getContent());
      } else {
        assertFalse(
            "Build file should not have settings dictionary", file.getSettings().isPresent());
      }
    }
  }

  private void assertSourcesNotInSourcesPhase(PBXTarget target, ImmutableSet<String> sources) {
    ImmutableSet.Builder<String> absoluteSourcesBuilder = ImmutableSet.builder();
    for (String name : sources) {
      absoluteSourcesBuilder.add(
          projectFilesystem.getRootPath().resolve(name).toAbsolutePath().normalize().toString());
    }

    Iterable<PBXBuildPhase> buildPhases =
        Iterables.filter(target.getBuildPhases(), PBXSourcesBuildPhase.class::isInstance);
    if (Iterables.size(buildPhases) == 0) {
      return;
    }

    ImmutableSet<String> absoluteSources = absoluteSourcesBuilder.build();
    PBXSourcesBuildPhase sourcesBuildPhase =
        (PBXSourcesBuildPhase) Iterables.getOnlyElement(buildPhases);
    for (PBXBuildFile file : sourcesBuildPhase.getFiles()) {
      String filePath = assertFileRefIsRelativeAndResolvePath(file.getFileRef());
      assertFalse(
          "Build phase should not contain this file " + filePath,
          absoluteSources.contains(filePath));
    }
  }

  private ImmutableMap<String, String> getBuildSettings(
      BuildTarget buildTarget, PBXTarget target, String config) {
    assertHasConfigurations(target, config);
    return ProjectGeneratorTestUtils.getBuildSettings(
        projectFilesystem, buildTarget, target, config);
  }

  private void assertPBXTargetHasDependency(
      PBXProject project, PBXTarget pbxTarget, String dependencyTargetName) {

    assertEquals(pbxTarget.getDependencies().size(), 1);
    PBXContainerItemProxy dependencyProxy = pbxTarget.getDependencies().get(0).getTargetProxy();

    PBXTarget dependency = assertTargetExistsAndReturnTarget(project, dependencyTargetName);
    assertEquals(dependencyProxy.getRemoteGlobalIDString(), dependency.getGlobalID());
    assertEquals(dependencyProxy.getContainerPortal(), project);
  }

  private Path getAbsoluteOutputForNode(TargetNode<?> node, ImmutableSet<TargetNode<?>> nodes) {
    TargetGraph targetGraph = TargetGraphFactory.newInstance(nodes);
    BuildRuleResolver ruleResolver = getActionGraphBuilderNodeFunction(targetGraph).apply(node);
    SourcePath nodeOutput = ruleResolver.getRule(node.getBuildTarget()).getSourcePathToOutput();
    return ruleResolver.getSourcePathResolver().getAbsolutePath(nodeOutput);
  }
}
