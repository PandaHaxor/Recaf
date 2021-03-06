package me.coley.recaf.bytecode.search;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Type;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import me.coley.event.Bus;
import me.coley.recaf.config.impl.ConfDisplay;
import me.coley.recaf.event.ClassOpenEvent;
import me.coley.recaf.event.FieldOpenEvent;
import me.coley.recaf.event.InsnOpenEvent;
import me.coley.recaf.event.MethodOpenEvent;
import me.coley.recaf.ui.FormatFactory;
import me.coley.recaf.ui.component.AccessButton.AccessContext;
import me.coley.recaf.util.Icons;

public class ResultTree extends TreeView<Result> {
	public ResultTree() {
		setShowRoot(false);
		setCellFactory(param -> new TreeCell<Result>() {
			@Override
			public void updateItem(Result item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					// Hide elements.
					// Items enter this state when 'hidden' in the tree.
					setText(null);
					setGraphic(null);
				} else if (item.getType() == ResultType.EMPTY) {
					setGraphic(new ImageView(Icons.CL_PACKAGE));
					setText(item.getText());
				} else if (item.getType() == ResultType.TYPE) {
					setGraphic(Icons.getClass(item.getCn().access));
					setText(trim(item.getCn().name));
				} else if (item.getType() == ResultType.FIELD) {
					setGraphic(Icons.getAccess(item.getFn().access, AccessContext.FIELD));
					setText(item.getFn().name);
				} else if (item.getType() == ResultType.METHOD) {
					setGraphic(Icons.getAccess(item.getMn().access, AccessContext.METHOD));
					if (ConfDisplay.instance().showSearchMethodType) {
						Type type = Type.getType(item.getMn().desc);
						String text = item.getMn().name + " " + FormatFactory.typeMethod(type).getText();
						setText(text);
					} else {
						setText(item.getMn().name);
					}
				} else if (item.getType() == ResultType.OPCODE) {
					setGraphic(FormatFactory.opcode(item.getAin(), item.getMn()));
					setText(null);
				}
			}

			/**
			 * Trim the text to a last section, if needed.
			 * 
			 * @param item
			 *            Internal class name.
			 * @return Simple name.
			 */
			private String trim(String item) {
				return item.indexOf("/") > 0 ? item.substring(item.lastIndexOf("/") + 1) : item;
			}
		});
		setOnMouseClicked(e -> {
			// Double click to open class
			if (e.getClickCount() == 2) {
				ResultTreeItem item = (ResultTreeItem) getSelectionModel().getSelectedItem();
				if (item != null && !item.isDir) {
					Result res = item.getValue();
					switch (res.getType()) {
					case EMPTY:
						break;
					case FIELD:
						Bus.post(new ClassOpenEvent(res.getCn()));
						Bus.post(new FieldOpenEvent(res.getCn(), res.getFn(), this));
						break;
					case METHOD:
						Bus.post(new ClassOpenEvent(res.getCn()));
						Bus.post(new MethodOpenEvent(res.getCn(), res.getMn(), this));
						break;
					case OPCODE:
						Bus.post(new ClassOpenEvent(res.getCn()));
						Bus.post(new InsnOpenEvent(res.getCn(), res.getMn(), res.getAin()));
						break;
					case TYPE:
						Bus.post(new ClassOpenEvent(res.getCn()));
						break;
					}
				}
			}
		});
	}
	
	public void setSearchResults(List<Result> search) {
		ResultTreeItem root = getNodesForDirectory(search);
		root.sortChildren();
		setRoot(root);
	}

	private static ResultTreeItem getNodesForDirectory(List<Result> search) {
		ResultTreeItem root = new ResultTreeItem("Results");
		search.forEach(result -> {
			ResultTreeItem r = root;
			String[] parts = result.getParts();
			for (int i = 0; i < parts.length; i++) {
				String part = parts[i];
				if (i == parts.length - 1) {
					// add final file
					r.add(part, result);
				} else {
					if (r.hasChild(part)) {
						// navigate to sub-directory
						r = r.getChild(part);
					} else {
						// add sub-dir
						int diff = (parts.length - i);
						boolean isOpp = result.getType() == ResultType.OPCODE;
						Result tempR = Result.empty(part);
						if (isOpp && diff == 2) {
							tempR = Result.method(result.getCn(), result.getMn());
						} else if (isOpp && diff == 3) {
							tempR = Result.type(result.getCn());
						} else if (diff == 2) {
							tempR = Result.type(result.getCn());
						}
						r = r.add(part, tempR);
					}
				}
			}
		});
		return root;
	}

	/**
	 * Wrapper for TreeItem children set. Allows more file-system-like access.
	 * 
	 * @author Matt
	 */
	public static class ResultTreeItem extends TreeItem<Result> implements Comparable<Result> {
		private final Map<String, ResultTreeItem> namedChildren = new HashMap<>();
		private final boolean isDir;

		private ResultTreeItem(Result result) {
			setValue(result);
			isDir = false;
		}

		private ResultTreeItem(String name) {
			setValue(Result.empty(name));
			isDir = true;
		}

		ResultTreeItem add(String name, Result result) {
			ResultTreeItem rti = new ResultTreeItem(result);
			namedChildren.put(name, rti);
			getChildren().add(rti);
			return rti;
		}

		public void sortChildren() {
			// getChildren().sort(Comparator.comparing(t -> t.getValue()));
			getChildren().sort(new Comparator<TreeItem<Result>>() {
				@Override
				public int compare(TreeItem<Result> a, TreeItem<Result> b) {
					return a.getValue().compareTo(b.getValue());
				}
			});
			for (int i = 0; i < getChildren().size(); i++) {
				((ResultTreeItem) getChildren().get(i)).sortChildren();
			}

		}

		ResultTreeItem getChild(String name) {
			return namedChildren.get(name);
		}

		boolean hasChild(String name) {
			return namedChildren.containsKey(name);
		}

		@Override
		public int compareTo(Result o) {
			return getValue().compareTo(o);
		}
	}
}
