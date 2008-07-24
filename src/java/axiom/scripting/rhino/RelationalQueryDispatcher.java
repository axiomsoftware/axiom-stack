package axiom.scripting.rhino;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import org.apache.lucene.search.IndexSearcher;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.framework.core.TypeManager;
import axiom.objectmodel.db.DbColumn;
import axiom.objectmodel.db.DbMapping;
import axiom.objectmodel.db.DbSource;
import axiom.objectmodel.db.Key;
import axiom.objectmodel.db.Node;
import axiom.objectmodel.db.NodeManager;
import axiom.objectmodel.db.Relation;
import axiom.objectmodel.db.Transactor;
import axiom.scripting.rhino.LuceneQueryDispatcher.LuceneQueryParams;
import axiom.scripting.rhino.extensions.filter.AndFilterObject;
import axiom.scripting.rhino.extensions.filter.FilterObject;
import axiom.scripting.rhino.extensions.filter.IFilter;
import axiom.scripting.rhino.extensions.filter.NativeFilterObject;
import axiom.scripting.rhino.extensions.filter.NotFilterObject;
import axiom.scripting.rhino.extensions.filter.OpFilterObject;
import axiom.scripting.rhino.extensions.filter.OrFilterObject;
import axiom.scripting.rhino.extensions.filter.QuerySortField;
import axiom.scripting.rhino.extensions.filter.SortObject;
import axiom.util.ResourceProperties;

public class RelationalQueryDispatcher extends QueryDispatcher {

	public RelationalQueryDispatcher(Application app, String name)
			throws Exception {
		super(app, name);
	}

	public Object documentToNode(Object d, Scriptable global, final int mode,
			final boolean executedInTransactor) throws Exception {
		return new Object();
	}

	public Object filterHits(Object prototype, Object filter,
			Object params) {
		return new Object();
	}

	public int filterHitsLength(Object prototype, Object filter,
			LuceneQueryParams params) throws Exception {
		return 0;
	}

	public ArrayList executeQuery(ArrayList prototypes,
		IFilter filter, Object options) throws Exception {
		if (filter == null) {
			throw new Exception(
					"Filter object is null in executeRelationalQuery().");
		}

		final GlobalObject global = this.core != null ? this.core.global : null;
		ArrayList results = new ArrayList();
		final int length = prototypes.size();
		NodeManager nmgr = this.app.getNodeManager();
		final TypeManager tmgr = this.app.typemgr;
		final ResourceProperties combined_props = new ResourceProperties();
		final boolean executedInTransactor = Thread.currentThread() instanceof Transactor;
		SortObject sort = getSortObject(options);

		for (int i = 0; i < length; i++) {
			String prototype = ((DbMapping) prototypes.get(i)).getTypeName();
			Prototype p = tmgr.getPrototype(prototype);
			if (p != null) {
				combined_props.putAll(p.getTypeProperties());
			}
		}

		for (int i = 0; i < length; i++) {
			DbMapping dbm = (DbMapping) prototypes.get(i);

			PreparedStatement pstmt = null;
			ResultSet rs = null;
			String query = null;

			try {
				Connection con = dbm.getConnection();
				// set connection to read-only mode
				if (!con.isReadOnly())
					con.setReadOnly(true);

				int dbtype = DbSource.UNKNOWN;
				DbSource dbsource = dbm.getDbSource();
				if (dbsource != null) {
					dbtype = dbsource.getDbType();
				}

				final int maxResults = getMaxResults(options);
				
				DbColumn[] columns = dbm.getColumns();
				Relation[] joins = dbm.getJoins();
				StringBuffer b = dbm.getSelect(null);
				
				/*if (dbtype != DbSource.MYSQL && maxResults != -1) {
					b.insert(0, "SET ROWCOUNT " + maxResults + " ");
				}*/

				ArrayList filterObjs = new ArrayList();
				StringBuffer filterClause = new StringBuffer();
				boolean whereAdded = this.filterToSql(filterClause, dbm,
						filter, filterObjs, false, false);

				String prefix = " AND ";
				if (!whereAdded) {
					if (dbm.getTableCount() > 1 && dbm.getJoins().length > 0) {
						b.append("WHERE ");
						prefix = "";
						whereAdded = true;
					}
				} else {
					filterClause.append(") ");
					b.append(filterClause.toString());
				}

				dbm.addJoinConstraints(b, prefix);

				b.append(dbm.getTableJoinClause(whereAdded ? 0 : 1));
				
				if (sort != null) {
					String sortQuery = getRelationalSortQuery(sort, dbm);
					if (sortQuery != null && sortQuery.length() > 0) {
						b.append(" ").append(sortQuery);
					}
				}
				
				/*if (maxResults != -1 && dbtype == DbSource.MYSQL) {
					b.append(" ").append("LIMIT ").append(maxResults);
				}*/
				
				query = b.toString();

				pstmt = con.prepareStatement(query);
				
				if (maxResults != -1) pstmt.setMaxRows(maxResults);

				int count = 0;
				int filtersSize = filterObjs.size();
				for (int z = 0; z < filtersSize; z++) {
					FilterObject fobj = (FilterObject) filterObjs.get(z);
					Object[] keys = fobj.getKeys().toArray();
					for (int y = 0; y < keys.length; y++) {
						String key = keys[y].toString();
						Object[] values = (Object[]) fobj.getValueForKey(key);

						Relation rel = dbm.getExactPropertyRelation(key);
						int columnType = -1;
						if (rel != null) {
							columnType = rel.getColumnType();
						} else {
							columnType = java.sql.Types.VARCHAR; // The key's
							// sql type
						}

						if (columnType == -1) {
							this.app.logError("QueryBean.executeRelationalQuery(): Could not find a db column for the property "
											+ key);
							continue;
						}

						final int vallen = values == null ? 0 : values.length;
						for (int j = 0; j < vallen; j++) {
							pstmt.setObject(++count, values[j], columnType);
						}
					}
				}

				rs = pstmt.executeQuery();

				while (rs.next()) {
					Node cnode = nmgr.createNode(dbm, rs, columns, 0);

					nmgr.fetchJoinedNodes(rs, joins, columns.length);

					Key key = cnode.getKey();
					Node node = null;
					if (executedInTransactor) {
						node = nmgr.getNodeFromTransaction(key);
					}

					if (node == null) {
						node = nmgr.getNodeFromCache(key);

						if (node == null) {
							node = cnode;
							if (executedInTransactor) {
								node = nmgr.conditionalCacheUpdate(node);
							}
						}

						if (executedInTransactor) {
							nmgr.conditionalNodeVisit(key, node);
						}
					}

					if (global != null) {
						results.add(Context.toObject(node, global));
					} else {
						results.add(node);
					}
					if (maxResults != -1 && results.size() >= maxResults) {
						return results;
					}
				}
			} catch (Exception ex) {
				app.logError(ErrorReporter.errorMsg(this.getClass(), "executeQuery") 
						+ "Failed on the following query: " + query, ex);
			} finally {
				if (rs != null) {
					try {
						rs.close();
					} catch (Exception fdma) {
						app.logError(ErrorReporter.errorMsg(this.getClass(), "executeQuery"), fdma);
					}
					rs = null;
				}
				if (pstmt != null) {
					try {
						pstmt.close();
					} catch (Exception fdma) {
						app.logError(ErrorReporter.errorMsg(this.getClass(), "executeQuery"), fdma);
					}
					pstmt = null;
				}
			}
		}

		return results;
	}

	private boolean filterToSql(StringBuffer b, DbMapping dbm, IFilter filter,
			ArrayList filterObjs, boolean whereAdded, boolean isNotFilter)
			throws Exception {
		if (filter instanceof FilterObject) {
			FilterObject fobj = (FilterObject) filter;
			filterObjs.add(fobj);
			Object[] keys = fobj.getKeys().toArray();
			String equalityCheck = isNotFilter ? " <> " : " = ";
			boolean addAnd = false;
			for (int z = 0; z < keys.length; z++) {
				String key = keys[z].toString();
				Object[] values = (Object[]) fobj.getValueForKey(key);

				String columnName = null;
				if ("_id".equals(key)) {
					columnName = /* dbm.getTableName(0) + "." + */dbm
							.getIDField();
				} else {
					Relation rel = dbm.getExactPropertyRelation(key);
					if (rel != null) {
						columnName = rel.getDbField();
					}
				}

				if (columnName == null) {
					this.app
							.logError("QueryBean.executeRelationalQuery(): Could not find a"
									+ " db column for the property "
									+ key
									+ ", so we are not using it in the search criterion.");
					throw new Exception(
							"QueryBean.executeRelationalQuery(): Could not find a"
									+ " db column for the property "
									+ key
									+ ", so we are not using it in the search criterion.");
				}

				final int vallen = values == null ? 0 : values.length;

				if (vallen > 0) {
					if (!whereAdded) {
						b.append("WHERE (");
						whereAdded = true;
					}
					if (addAnd) {
						b.append(" AND ");
					} else {
						addAnd = true;
					}
					b.append("(");

					for (int j = 0; j < vallen; j++) {
						if (j > 0) {
							b.append(" OR ");
						}
						b.append(columnName).append(equalityCheck).append("?");
					}

					b.append(") ");
				}
			}
		} else if (filter instanceof OpFilterObject) {
			OpFilterObject ofobj = (OpFilterObject) filter;
			IFilter[] filters = ofobj.getFilters();
			for (int i = 0; i < filters.length; i++) {
				String joiner = "";
				boolean isNot = false;
				if (ofobj instanceof NotFilterObject) {
					isNot = true;
				}

				if (i > 0) {
					if (ofobj instanceof OrFilterObject) {
						joiner = " OR ";
					} else if (ofobj instanceof AndFilterObject) {
						joiner = " AND ";
					}
				} else {
					if (!whereAdded) {
						b.append("WHERE (");
						whereAdded = true;
					}
				}
				b.append(joiner).append("(");
				filterToSql(b, dbm, filters[i], filterObjs, whereAdded, isNot);
				b.append(")");
			}
		} else if (filter instanceof NativeFilterObject) {
			if (!whereAdded) {
				b.append("WHERE (");
				whereAdded = true;
			}
			b.append(((NativeFilterObject) filter).getNativeQuery());
		}

		return whereAdded;
	}


    private String getRelationalSortQuery(SortObject sort, DbMapping dbm) {
        StringBuffer sb = new StringBuffer();
        QuerySortField[] fields = sort.getSortFields();
        int length;
        if (fields != null && (length = fields.length) > 0) {
            boolean addedClause = false;
            for (int i = 0; i < length; i++) {
                final String currField = fields[i].getField();
                String dbField = null;
                if ("_id".equals(currField)) {
                    dbField = dbm.getIDField();
                } else {
                    Relation rel = dbm.getExactPropertyRelation(currField);
                    if (rel != null) {
                        dbField = rel.getDbField();
                    }
                }
                if (dbField != null) {
                    if (!addedClause) {
                        sb.append("ORDER BY ");
                        addedClause = true;
                    }
                    sb.append(dbField).append(" ");
                    sb.append(SortObject.getRelationalSortValue(fields[i].getOrder()));
                    if (i < length - 1) {
                        sb.append(", ");
                    }
                }
            }
        }
        
        return sb.toString();
    }

    private int numResultsRelationalQuery(ArrayList prototypes, IFilter filter) 
    throws Exception {        
        
        if (filter == null) {
            throw new Exception("Filter object is null in executeRelationalQuery().");
        }

        int numResults = 0;
        final int length = prototypes.size();
        final TypeManager tmgr = this.app.typemgr;
        final ResourceProperties combined_props = new ResourceProperties();

        for (int i = 0; i < length; i++) {
            String prototype = ((DbMapping) prototypes.get(i)).getTypeName();
            Prototype p = tmgr.getPrototype(prototype);
            if (p != null) {
                combined_props.putAll(p.getTypeProperties());
            }
        }

        for (int i = 0; i < length; i++) {
            DbMapping dbm = (DbMapping) prototypes.get(i);

            PreparedStatement pstmt = null;
            ResultSet rs = null;
            String query = null;

            try {
                Connection con = dbm.getConnection();
                // set connection to read-only mode
                if (!con.isReadOnly()) con.setReadOnly(true);

                StringBuffer b = dbm.getSelectCount(null);

                ArrayList filterObjs = new ArrayList();
                StringBuffer filterClause = new StringBuffer();
                boolean whereAdded = this.filterToSql(filterClause, dbm, filter, 
                        filterObjs, false, false);

                String prefix = " AND ";
                if (!whereAdded) {
                    if (dbm.getTableCount() > 1 && dbm.getJoins().length > 0) {
                        b.append("WHERE ");
                        prefix = "";
                        whereAdded = true;
                    }
                } else {
                    filterClause.append(") ");
                    b.append(filterClause.toString());
                }

                dbm.addJoinConstraints(b, prefix);

                b.append(dbm.getTableJoinClause(whereAdded ? 0 : 1));

                query = b.toString();

                pstmt = con.prepareStatement(query);

                int count = 0;
                int filtersSize = filterObjs.size();
                for (int z = 0; z < filtersSize; z++) {
                    FilterObject fobj = (FilterObject) filterObjs.get(z);
                    Object[] keys = fobj.getKeys().toArray();
                    for (int y = 0; y < keys.length; y++) {
                        String key = keys[y].toString();
                        Object[] values = (Object[]) fobj.getValueForKey(key);

                        Relation rel = dbm.getExactPropertyRelation(key);
                        int columnType = -1;
                        if (rel != null) {
                            columnType = rel.getColumnType();
                        } else {
                            columnType = java.sql.Types.VARCHAR; // The key's sql type
                        }

                        if (columnType == -1) {
                            this.app.logError(ErrorReporter.errorMsg(this.getClass(), "numResultsRelationalQuery") 
                            		+ "Could not find a db column for the property " + key);
                            continue;
                        }

                        final int vallen = values == null ? 0 : values.length;
                        for (int j = 0; j < vallen; j++) {
                            pstmt.setObject(++count, values[j], columnType);
                        }
                    }
                }

                rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    numResults = rs.getInt(1);
                }
                
            } catch (Exception ex) {
                app.logError(ErrorReporter.errorMsg(this.getClass(), "numResultsRelationalQuery") 
                		+ "Failed on the following query: " + query, ex);
            } finally {
                if (rs != null) {
                    try { 
                        rs.close(); 
                    } catch (Exception fdma) { 
                        app.logError(ErrorReporter.errorMsg(this.getClass(), "numResultsRelationalQuery"), fdma);
                    }
                    rs = null;
                }
                if (pstmt != null) {
                    try { 
                        pstmt.close(); 
                    } catch (Exception fdma) { 
                    	app.logError(ErrorReporter.errorMsg(this.getClass(), "numResultsRelationalQuery"), fdma);
                    }
                    pstmt = null;
                }
            }
        }

        return numResults;
    } 

    public Object hits(ArrayList prototypes, IFilter filter, 
    		Object options) throws Exception {
    	return Context.getCurrentContext().newArray(this.core.global, new Object[0]);
    }

    public int getHitCount(ArrayList prototypes, IFilter filter, Object options) 
    throws Exception {
    	int length = 0;
        ArrayList relationalResults = 
            executeQuery(prototypes, filter, options);
        length = relationalResults.size();
		return length;
	} 

}
