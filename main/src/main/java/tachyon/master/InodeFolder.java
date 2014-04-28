/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tachyon.master;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tachyon.io.Utils;
import tachyon.thrift.ClientFileInfo;

/**
 * Tachyon file system's folder representation in master.
 */
public class InodeFolder extends Inode {
  /**
   * Create a new InodeFile from an image stream.
   * 
   * @param is
   *          the image stream
   * @return
   * @throws IOException
   */
  static InodeFolder loadImage(DataInputStream is) throws IOException {
    long creationTimeMs = is.readLong();
    int fileId = is.readInt();
    String fileName = Utils.readString(is);
    int parentId = is.readInt();

    int numberOfChildren = is.readInt();
    Inode[] children = new Inode[numberOfChildren];
    for (int k = 0; k < numberOfChildren; k ++) {
      byte type = is.readByte();
      if (type == Image.T_INODE_FILE) {
        children[k] = InodeFile.loadImage(is);
      } else {
        children[k] = InodeFolder.loadImage(is);
      }
    }

    InodeFolder folder = new InodeFolder(fileName, fileId, parentId, creationTimeMs);
    folder.addChildren(children);
    return folder;
  }

  private Set<Inode> mChildren = new HashSet<Inode>();

  public InodeFolder(String name, int id, int parentId, long creationTimeMs) {
    super(name, id, parentId, true, creationTimeMs);
  }

  public synchronized void addChild(Inode child) {
    mChildren.add(child);
  }

  public synchronized void addChildren(Inode[] children) {
    for (Inode i : children) {
      addChild(i);
    }
  }

  @Override
  public ClientFileInfo generateClientFileInfo(String path) {
    ClientFileInfo ret = new ClientFileInfo();

    ret.id = getId();
    ret.name = getName();
    ret.path = path;
    ret.checkpointPath = "";
    ret.length = 0;
    ret.blockSizeByte = 0;
    ret.creationTimeMs = getCreationTimeMs();
    ret.complete = true;
    ret.folder = true;
    ret.inMemory = true;
    ret.needPin = false;
    ret.needCache = false;
    ret.blockIds = null;
    ret.dependencyId = -1;

    return ret;
  }

  public synchronized Inode getChild(String name) {
    for (Inode i : mChildren) {
      if (i.getName().equals(name)) {
        return i;
      }
    }
    return null;
  }

  public synchronized Inode getChild(int fid) {
    for (Inode i : mChildren) {
      if (i.getId() == fid) {
        return i;
      }
    }
    return null;
  }

  public synchronized List<Integer> getChildrenIds() {
    List<Integer> ret = new ArrayList<Integer>(mChildren.size());
    for (Inode i : mChildren) {
      ret.add(i.getId());
    }
    return ret;
  }

  /**
   * Returns a list of the folder's children.
   * 
   * @return A list of the children inodes.
   */
  public synchronized Set<Inode> getChildren() {
    return mChildren;
  }

  public synchronized int getNumberOfChildren() {
    return mChildren.size();
  }

  /**
   * Removes the given inode from the folder.
   * 
   * @param i
   *          The Inode to remove
   * @return true if the inode was removed, false otherwise.
   */
  public synchronized boolean removeChild(Inode i) {
    return mChildren.remove(i);
  }

  /**
   * Removes the given child from the folder.
   * 
   * @param name
   *          The name of the Inode to remove.
   * @return true if the inode was removed, false otherwise.
   */
  public synchronized boolean removeChild(String name) {
    for (Inode i : mChildren) {
      if (i.getName().equals(name)) {
        mChildren.remove(i);
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("InodeFolder(");
    sb.append(super.toString()).append(",").append(mChildren).append(")");
    return sb.toString();
  }

  @Override
  public void writeImage(DataOutputStream os) throws IOException {
    os.writeByte(Image.T_INODE_FOLDER);

    os.writeLong(getCreationTimeMs());
    os.writeInt(getId());
    Utils.writeString(getName(), os);
    os.writeInt(getParentId());

    List<Integer> children = getChildrenIds();
    os.writeInt(children.size());
    for (Inode inode : getChildren()) {
      inode.writeImage(os);
    }
  }
}
