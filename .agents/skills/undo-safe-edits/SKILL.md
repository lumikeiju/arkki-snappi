---
name: undo-safe-edits
description: Undo-safe OSM data mutation for arkki-snappi — every write must go through JOSM Command objects. Use when adding, changing, or deleting nodes/ways, or when keeping the wayCorners cache in sync.
triggers:
  - command pattern
  - undo
  - AddCommand
  - ChangeCommand
  - DeleteCommand
  - SequenceCommand
  - data set mutation
  - wayCorners
paths:
  - src/main/java/org/openstreetmap/josm/plugins/arkkisnappi/SnappiMode.java
---

# Undo-Safe OSM Edits

**Rule: never mutate a `DataSet` directly.** Every write must be a JOSM `Command` so undo/redo works.

## Template

```java
DataSet ds = getLayerManager().getEditDataSet();
List<Command> cmds = new ArrayList<>();
cmds.add(new AddCommand(ds, newNode));
Way updated = new Way(existingWay);          // clone, then mutate
updated.setNodes(newNodeList);
cmds.add(new ChangeCommand(existingWay, updated));
cmds.add(new DeleteCommand(ds, Collections.singleton(orphan)));
UndoRedoHandler.getInstance().add(new SequenceCommand(tr("label"), cmds));
```

## Node merging (simplify)

- `findMatchingNode(nodes, en)` — exact position match within 1 mm.
- `findBestMatchingNode(nodes, en)` — among coincident candidates, prefer the most useful node; priority = `(hasKeys ? 1 : 0) + max(0, parentWays - 1)`.
- Delete orphaned nodes only when `!node.hasKeys() && node.getParentWays().size() <= 1`.

## wayCorners cache

Keep `wayCorners[]` in sync with the committed way after every geometry change:

- Updated in `commitExtrude()`, `simplifyWay()`, `shrinkwrapWay()`; rebuilt by `syncWayCorners()`.
- After `commitExtrude()` updates corners, run `shrinkwrapWay()` then `simplifyWay()` — same pair on `finishShape()`.
