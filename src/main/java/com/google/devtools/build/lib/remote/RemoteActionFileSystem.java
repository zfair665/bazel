package com.google.devtools.build.lib.remote;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.ActionInput;
import com.google.devtools.build.lib.actions.ActionInputMap;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.FileArtifactValue;
import com.google.devtools.build.lib.actions.MetadataConsumer;
import com.google.devtools.build.lib.vfs.DelegateFileSystem;
import com.google.devtools.build.lib.vfs.FileSystem;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

public class RemoteActionFileSystem extends DelegateFileSystem {

  private final Path execRoot;
  private final Path outputBase;
  private final ActionInputMap inputArtifactData;
  private final ImmutableMap<Path, ActionInput> outputs;
  private final RemoteActionInputFetcher inputFetcher;
  private MetadataConsumer metadataInjector;

  public RemoteActionFileSystem(FileSystem localDelegate,
      PathFragment execRootFragment,
      String relativeOutputPath,
      ActionInputMap inputArtifactData,
      Iterable<? extends ActionInput> outputs,
      RemoteActionInputFetcher inputFetcher) {
    super(localDelegate);
    this.execRoot = getPath(Preconditions.checkNotNull(execRootFragment, "execRootFragment"));
    this.outputBase = execRoot.getRelative(Preconditions.checkNotNull(relativeOutputPath, "relativeOutputPath"));
    this.inputArtifactData = Preconditions.checkNotNull(inputArtifactData, "inputArtifactData");
    ImmutableMap.Builder<Path, ActionInput> outputsBuilder = ImmutableMap.builder();
    for (ActionInput output : Preconditions.checkNotNull(outputs, "outputs")) {
      outputsBuilder.put(execRoot.getRelative(output.getExecPath()), output);
    }
    this.outputs = outputsBuilder.build();
    this.inputFetcher = Preconditions.checkNotNull(inputFetcher, "inputFetcher");
  }

  void updateActionFileSystemContext(MetadataConsumer metadataInjector) {
    this.metadataInjector = metadataInjector;
  }

  @Nullable
  private FileArtifactValue getRemoteMetadata(PathFragment pathFragment) {
    if (!pathFragment.startsWith(outputBase.asFragment())) {
      return null;
    }
    return getRemoteMetadata(execPathString(pathFragment));
  }

   @Nullable
   private FileArtifactValue getRemoteMetadata(Path path) {
     if (!path.startsWith(outputBase)) {
       return null;
     }
     return getRemoteMetadata(execPathString(path));
   }

   @Nullable
   private FileArtifactValue getRemoteMetadata(String execPathString) {
     FileArtifactValue m = inputArtifactData.getMetadata(execPathString);
     if (m != null && m.isRemote()) {
       return m;
     }
     return null;
   }

   private ActionInput getOutputOrFail(Path path) throws IOException {
     ActionInput output = outputs.get(path);
     if (output == null) {
       throw new IOException(String.format("Unknown output '%s'", path));
     }
     return output;
   }

   @Override
   protected InputStream getInputStream(Path path) throws IOException {
     FileArtifactValue metadata = getRemoteMetadata(path);
     if (metadata != null) {
       System.err.println("Staging " + path);
       try {
         inputFetcher.downloadFile(delegatePath(path), metadata);
       } catch (InterruptedException e) {
         throw new IOException(String.format("Received interrupt while fetching file '%s'",
             path), e);
       }
     }
     return super.getInputStream(path);
   }

   @Override
   protected void createSymbolicLink(Path linkPath, PathFragment targetFragment) throws IOException {
     FileArtifactValue targetMetadata = getRemoteMetadata(targetFragment);
     if (targetMetadata != null) {
       System.err.println("Injecting symbolic link from " + linkPath + " to " + targetFragment);
       // The target is a file that's stored remotely. Don't physically create the symlink on disk
       // but only inject the metadata into skyframe.
       metadataInjector.accept((Artifact) getOutputOrFail(linkPath), targetMetadata);
     } else {
       // Invariant: targetFragment is a local file/directory
       super.createSymbolicLink(linkPath, targetFragment);
     }
   }

  @Override
  protected boolean isFile(Path path, boolean followSymlinks) {
    FileArtifactValue m = getRemoteMetadata(path);
    return m != null || super.isFile(path, followSymlinks);
  }

  @Override
  protected boolean isReadable(Path path) throws IOException {
    FileArtifactValue m = getRemoteMetadata(path);
    return m != null || super.isReadable(path);
  }

  @Override
  protected boolean isWritable(Path path) throws IOException {
    FileArtifactValue m = getRemoteMetadata(path);
    return m != null || super.isWritable(path);
  }

  @Override
  protected boolean isExecutable(Path path) throws IOException {
    FileArtifactValue m = getRemoteMetadata(path);
    return m != null || super.isExecutable(path);
  }

  private String execPathString(Path path) {
    return path.relativeTo(execRoot).getPathString();
   }

  private String execPathString(PathFragment pathFragment) {
    return pathFragment.relativeTo(execRoot.asFragment()).getPathString();
  }
}
