package mesosphere.marathon.experimental.storage;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;

/**
 * NOTE: This is a copy of Zookeeper's [[PathTrie]] class modified to provide additional access
 * to node's children.
 *
 * Original description: a class that implements prefix matching for
 * components of a filesystem path. the trie
 * looks like a tree with edges mapping to
 * the component of a path.
 * example /ab/bc/cf would map to a trie
 *           /
 *        ab/
 *        (ab)
 *      bc/
 *       /
 *      (bc)
 *   cf/
 *   (cf)
 */
public class PathTrie {
    /**
     * the logger for this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(PathTrie.class);

    /**
     * the root node of PathTrie
     */
    private final TrieNode rootNode ;

    public static class TrieNode {
        final HashMap<String, TrieNode> children;
        TrieNode parent = null;
        /**
         * create a trienode with parent
         * as parameter
         * @param parent the parent of this trienode
         */
        private TrieNode(TrieNode parent) {
            children = new HashMap<String, TrieNode>();
            this.parent = parent;
        }

        /**
         * get the parent of this node
         * @return the parent node
         */
        TrieNode getParent() {
            return this.parent;
        }

        /**
         * set the parent of this node
         * @param parent the parent to set to
         */
        void setParent(TrieNode parent) {
            this.parent = parent;
        }

        /**
         * add a child to the existing node
         * @param childName the string name of the child
         * @param node the node that is the child
         */
        void addChild(String childName, TrieNode node) {
            synchronized(children) {
                if (children.containsKey(childName)) {
                    return;
                }
                children.put(childName, node);
            }
        }

        /**
         * delete child from this node
         * @param childName the string name of the child to
         * be deleted
         */
        void deleteChild(String childName) {
            synchronized(children) {
                if (!children.containsKey(childName)) {
                    return;
                }
                TrieNode childNode = children.get(childName);
                childNode.setParent(null);
                children.remove(childName);
            }
        }

        /**
         * return the child of a node mapping
         * to the input childname
         * @param childName the name of the child
         * @return the child of a node
         */
        TrieNode getChild(String childName) {
            synchronized(children) {
                if (!children.containsKey(childName)) {
                    return null;
                }
                else {
                    return children.get(childName);
                }
            }
        }

        /**
         * get the list of children of this
         * trienode.
         * @return the string list of its children
         */
        String[] getChildren() {
            synchronized(children) {
                return children.keySet().toArray(new String[0]);
            }
        }

        /**
         * get the map of children nodes for this trienode. An unmodifiable map is returned to prevent
         *
         * @return
         */
        Map<String, TrieNode> getChildrenNodes() {
            synchronized(children) {
                return Collections.unmodifiableMap(children);
            }
        }

        /**
         * get the string representation
         * for this node
         */
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Children of trienode: ");
            synchronized(children) {
                for (String str: children.keySet()) {
                    sb.append(" " + str);
                }
            }
            return sb.toString();
        }
    }

    /**
     * construct a new PathTrie with
     * a root node of /
     */
    public PathTrie() {
        this.rootNode = new TrieNode(null);
    }

    /**
     * add a path to the path trie
     * @param path
     */
    public void addPath(String path) {
        if (path == null) {
            return;
        }
        String[] pathComponents = path.split("/");
        TrieNode parent = rootNode;
        String part = null;
        if (pathComponents.length <= 1) {
            throw new IllegalArgumentException("Invalid path " + path);
        }
        for (int i=1; i<pathComponents.length; i++) {
            part = pathComponents[i];
            if (parent.getChild(part) == null) {
                parent.addChild(part, new TrieNode(parent));
            }
            parent = parent.getChild(part);
        }
    }

    /**
     * delete a path from the trie
     * @param path the path to be deleted
     */
    public void deletePath(String path) {
        if (path == null) {
            return;
        }
        String[] pathComponents = path.split("/");
        TrieNode parent = rootNode;
        String part = null;
        if (pathComponents.length <= 1) {
            throw new IllegalArgumentException("Invalid path " + path);
        }
        for (int i=1; i<pathComponents.length; i++) {
            part = pathComponents[i];
            if (parent.getChild(part) == null) {
                //the path does not exist
                LOG.warn("Can't delete path {} since it doesn't exist.", path);
                return;
            }
            parent = parent.getChild(part);
            LOG.info("{}",parent);
        }
        TrieNode realParent  = parent.getParent();
        realParent.deleteChild(part);
    }

    /**
     * return the largest prefix for the input path.
     * @param path the input path
     * @return the largest prefix for the input path.
     */
    public String findMaxPrefix(String path) {
        if (path == null) {
            return null;
        }
        if ("/".equals(path)) {
            return path;
        }
        String[] pathComponents = path.split("/");
        TrieNode parent = rootNode;
        List<String> components = new ArrayList<String>();
        if (pathComponents.length <= 1) {
            throw new IllegalArgumentException("Invalid path " + path);
        }
        int i = 1;
        String part = null;
        StringBuilder sb = new StringBuilder();
        int lastindex = -1;
        while((i < pathComponents.length)) {
            if (parent.getChild(pathComponents[i]) != null) {
                part = pathComponents[i];
                parent = parent.getChild(part);
                components.add(part);
            }
            else {
                break;
            }
            i++;
        }
        for (int j=0; j< (lastindex+1); j++) {
            sb.append("/" + components.get(j));
        }
        return sb.toString();
    }

    /**
     * clear all nodes
     */
    public void clear() {
        for(String child : rootNode.getChildren()) {
            rootNode.deleteChild(child);
        }
    }

    /**************************************************************************
     *                           Additional methods                           *
     **************************************************************************/

    /**
     * return trie's root node. Useful when iterating through the trie.
     *
     * @return root node
     */
    public TrieNode getRoot() {
        return rootNode;
    }

    /**
     * return a trie's node for the given path. If the path doesn't exist null is returned
     *
     * @param path input path
     * @return node with the given path
     */
    public TrieNode getNode(String path) {
        if (path == null) {
            return null;
        }
        if ("/".equals(path)) {
            return rootNode;
        }
        String[] pathComponents = path.split("/");
        if (pathComponents.length <= 1) {
            throw new IllegalArgumentException("Invalid path " + path);
        }
        TrieNode parent = rootNode;
        String part = null;
        for (int i=1; i<pathComponents.length; i++) {
            part = pathComponents[i];
            if (parent.getChild(part) == null) {
                //the path does not exist
                return null;
            }
            parent = parent.getChild(part);
            LOG.debug("{}",parent);
        }
        return parent;
    }

    private void findLeafNodes(String path, @NotNull TrieNode node, Set<String> leafs) {
        if (node.getChildrenNodes().isEmpty()) {  // Found leaf node
            leafs.add(path);
        } else {
            node.getChildrenNodes().forEach((p, n) -> findLeafNodes(Paths.get(path, p).toString(), n, leafs));
        }
    }

    /**
     * return leaf nodes recursively starting with the given path.
     *
     * @param path input path
     * @return a map with leaf nodes
     */
    public Set<String> getLeafs(String path) {
        TrieNode node = getNode(path);
        if (node == null) {
            return null;
        }

        Set<String> set = new HashSet<>();
        findLeafNodes(path, node, set);
        return set;
    }

    /**
     * return the children names for the given path. If the path doesn't exist,
     * null is returned
     *
     * @param path the input path
     * @param fullPath if true, full path for the children node will be returned
     * @return a set of children names
     */
    public Set<String> getChildren(String path, Boolean fullPath) {
        TrieNode node = getNode(path);
        if (node == null) {
            return null;
        }
        if (!fullPath){
            return new HashSet<>(Arrays.asList(node.getChildren()));
        } else {
            return Arrays.asList(node.getChildren()).stream().map(p -> Paths.get(path, p).toString()).collect(Collectors.toSet());
        }
    }

}
