package com.comicnet.desktop.ui;

public interface Workspace extends AutoCloseable {
  default void onShown() {}

  default void onHidden() {}

  default void sessionChanged() {}

  @Override
  void close();
}
