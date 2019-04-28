package org.textup.override

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import grails.gorm.DetachedCriteria
import grails.gorm.PagedResultList
import org.codehaus.groovy.grails.orm.hibernate.query.AbstractHibernateQuery
import org.grails.datastore.mapping.query.Query
import org.hibernate.Criteria
import org.hibernate.sql.JoinType

// Inspired by: https://www.wijsmullerbros.com/2016/06/30/code-voorbeeld/
// Need to override all methods that use `withPopulatedQuery` because that method is marked as
// private and thus we are unable to directly override that method. We also can't intercept
// because self-calls don't trigger method dispatch
// Class we are extending: https://github.com/grails/grails-data-mapping/blob/3.x/grails-datastore-gorm/src/main/groovy/grails/gorm/DetachedCriteria.groovy

@GrailsTypeChecked
class DetachedJoinableCriteria<T> extends DetachedCriteria<T> {

    protected Collection<JoinInfo> joins

    DetachedJoinableCriteria(Class<T> targetClass, String alias = null) {
        super(targetClass, alias)
        joins = []
    }

    @Override
    DetachedJoinableCriteria<T> build(Closure callable) {
        DetachedJoinableCriteria newCriteria = clone()
        newCriteria.with(callable)
        return newCriteria
    }

    @Override
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected DetachedJoinableCriteria<T> clone() {
        def copy = new DetachedJoinableCriteria(targetClass, alias)
        copy.criteria = new ArrayList(criteria)
        final projections = new ArrayList(projections)
        copy.projections = projections
        // see http://groovy-lang.org/differences.html#_creating_instances_of_non_static_inner_classes
        copy.projectionList = new DetachedCriteria.DetachedProjections(copy, projections)
        copy.orders = new ArrayList(orders)
        copy.defaultMax = defaultMax
        copy.defaultOffset = defaultOffset
        copy.joins = new ArrayList(joins)
        return copy
    }

    @Override
    T get(Map args = Collections.emptyMap(), Closure additionalCriteria = null) {
        execute(args, additionalCriteria) { Query query -> query.singleResult() }
    }

    @Override
    List<T> list(Map args = Collections.emptyMap(), Closure additionalCriteria = null) {
        execute(args, additionalCriteria) { Query query ->
            if (args?.max) {
                return new PagedResultList(query)
            }
            return query.list()
        }
    }

    @Override
    Number count(Map args = Collections.emptyMap(), Closure additionalCriteria = null) {
        execute(args, additionalCriteria) { Query query ->
            query.projections().count()
            query.singleResult()
        } as Number
    }

    @Override
    Number count(Closure additionalCriteria) {
        execute(null, additionalCriteria) { Query query ->
            query.projections().count()
            query.singleResult()
        } as Number
    }

    @Override
    boolean asBoolean(Closure additionalCriteria = null) {
        execute(null, additionalCriteria) { Query query ->
            query.projections().count()
            ((Number)query.singleResult()) > 0
        }
    }

    // Modeled after: https://github.com/grails/grails-data-mapping/blob/3.x/grails-datastore-gorm/src/main/groovy/grails/gorm/DetachedCriteria.groovy#L132
    // Did not implement as a new `Query.Criterion` because then we would need to somehow override
    // the private method `addToJunction` in `Query.Criterion`
    DetachedJoinableCriteria<T> createAliasWithJoin(String association, String alias, JoinType type) {
        joins << new JoinInfo(association: association, alias: alias, joinType: type)
        this
    }

    // Helpers
    // -------

    protected <T> T execute(Map args, Closure additionalCriteria, Closure<T> doAction) {
        Map thisArgs = args ?: Collections.emptyMap()
        withPopulatedQuery(thisArgs, additionalCriteria, addJoinsBeforeExecuting(doAction))
    }

    protected <T> Closure<T> addJoinsBeforeExecuting(Closure<T> doAction) {
        return { Query query ->
            if (query instanceof AbstractHibernateQuery) {
                AbstractHibernateQuery hibernateQuery = query as AbstractHibernateQuery
                Criteria criteria = hibernateQuery.@criteria
                joins.each { JoinInfo jInfo ->
                    criteria.createAlias(jInfo.association, jInfo.alias, jInfo.joinType)
                }
            }
            doAction.call(query)
        }
    }

    protected static class JoinInfo {
        String association
        String alias
        JoinType joinType
    }
}
