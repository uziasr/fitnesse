// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.wiki;

import fitnesse.util.Clock;
import fitnesse.wiki.fs.WikiPageProperties;
import fitnesse.wikitext.SyntaxTree;
import fitnesse.wikitext.parser.*;

import static fitnesse.wiki.PageType.STATIC;

/**
 * This class adds support for FitNesse wiki text ({@link fitnesse.wikitext.parser.Parser}).
 */
public abstract class BaseWikitextPage extends BaseWikiPage implements WikitextPage {

  private final VariableSource variableSource;
  private ParsingPage parsingPage;
  private SyntaxTree syntaxTree;

  protected BaseWikitextPage(String name, VariableSource variableSource) {
    this(name, null, variableSource);
  }

  protected BaseWikitextPage(String name, WikiPage parent) {
    this(name, parent, parent instanceof BaseWikitextPage ? ((BaseWikitextPage) parent).variableSource : null);
  }

  protected BaseWikitextPage(String name, WikiPage parent, VariableSource variableSource) {
    super(name, parent);
    this.variableSource = variableSource;
  }

  protected VariableSource getVariableSource() {
    return variableSource;
  }

  @Override
  public String getVariable(String name) {
    Maybe<String> variable = getSyntaxTree().findVariable(name);
    if (variable.isNothing()) return null;

    SyntaxTree tree = new SyntaxTreeV2(SymbolProvider.variableDefinitionSymbolProvider);
    tree.parse(variable.getValue(), parsingPage);
    return tree.getHtml();
  }

  @Override
  public String getHtml() {
    parse();
    return syntaxTree.getHtml();
  }

  @Override
  public SyntaxTree getSyntaxTree() {
    parse();
    return syntaxTree;
  }

  private void parse() {
    if (syntaxTree == null) {
      parsingPage = makeParsingPage(this);
      syntaxTree = new SyntaxTreeV2();
      syntaxTree.parse(getData().getContent(), parsingPage);
    }
  }

  protected void resetCache() {
    parsingPage = null;
    syntaxTree = null;
  }

  public static ParsingPage makeParsingPage(BaseWikitextPage page) {
    ParsingPage.Cache cache = new ParsingPage.Cache();

    VariableSource compositeVariableSource = new CompositeVariableSource(
            new ApplicationVariableSource(page.variableSource),
            new PageVariableSource(page),
            new UserVariableSource(page.variableSource),
            cache,
            new ParentPageVariableSource(page),
            page.variableSource);
    return new ParsingPage(new WikiSourcePage(page), compositeVariableSource, cache);
  }

  public WikiPageProperty defaultPageProperties() {
    WikiPageProperties properties = new WikiPageProperties();
    properties.set(WikiPageProperty.EDIT);
    properties.set(WikiPageProperty.PROPERTIES);
    properties.set(WikiPageProperty.REFACTOR);
    properties.set(WikiPageProperty.WHERE_USED);
    properties.set(WikiPageProperty.RECENT_CHANGES);
    properties.set(WikiPageProperty.FILES);
    properties.set(WikiPageProperty.VERSIONS);
    properties.set(WikiPageProperty.SEARCH);
    properties.setLastModificationTime(Clock.currentDate());

    PageType pageType = PageType.getPageTypeForPageName(getName());

    if (STATIC.equals(pageType))
      return properties;

    properties.set(pageType.toString());
    return properties;
  }

  public static class UserVariableSource implements VariableSource {

    private final VariableSource variableSource;

    public UserVariableSource(VariableSource variableSource) {
      this.variableSource = variableSource;
    }

    @Override
    public Maybe<String> findVariable(String name) {
      if(variableSource instanceof UrlPathVariableSource){
        Maybe<String> result = ((UrlPathVariableSource) variableSource).findUrlVariable(name);
        if (!result.isNothing()) return result;
      }
      return Maybe.noString;
    }
  }

  public static class ParentPageVariableSource implements VariableSource {
    private final WikiPage page;

    public ParentPageVariableSource(WikiPage page) {

      this.page = page;
    }

    @Override
    public Maybe<String> findVariable(String name) {
      if (page.isRoot()) {
        // Get variable from
        return Maybe.noString;
      }
      WikiPage parentPage = page.getParent();
      if (parentPage instanceof WikitextPage) {
        return ((WikitextPage) parentPage).getSyntaxTree().findVariable(name);
      } else {
        String value = parentPage.getVariable(name);
        return value != null ? new Maybe<>(value) : Maybe.noString;
      }
    }
  }
}
