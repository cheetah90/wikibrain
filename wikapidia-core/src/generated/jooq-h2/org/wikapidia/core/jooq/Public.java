/**
 * This class is generated by jOOQ
 */
package org.wikapidia.core.jooq;

/**
 * This class is generated by jOOQ.
 */
@javax.annotation.Generated(value    = {"http://www.jooq.org", "3.0.0"},
                            comments = "This class is generated by jOOQ")
@java.lang.SuppressWarnings({ "all", "unchecked" })
public class Public extends org.jooq.impl.SchemaImpl {

	private static final long serialVersionUID = -1054440052;

	/**
	 * The singleton instance of <code>PUBLIC</code>
	 */
	public static final Public PUBLIC = new Public();

	/**
	 * No further instances allowed
	 */
	private Public() {
		super("PUBLIC");
	}

	@Override
	public final java.util.List<org.jooq.Sequence<?>> getSequences() {
		java.util.List result = new java.util.ArrayList();
		result.addAll(getSequences0());
		return result;
	}

	private final java.util.List<org.jooq.Sequence<?>> getSequences0() {
		return java.util.Arrays.<org.jooq.Sequence<?>>asList(
			org.wikapidia.core.jooq.Sequences.SYSTEM_SEQUENCE_03D7A14A_6C21_47D2_9CE2_7A555233F922,
			org.wikapidia.core.jooq.Sequences.SYSTEM_SEQUENCE_21E4540D_4E99_4234_AB39_DD9C8B903E52,
			org.wikapidia.core.jooq.Sequences.SYSTEM_SEQUENCE_A21F57A0_51F1_47B5_AD5C_C9F400A2D0D7);
	}

	@Override
	public final java.util.List<org.jooq.Table<?>> getTables() {
		java.util.List result = new java.util.ArrayList();
		result.addAll(getTables0());
		return result;
	}

	private final java.util.List<org.jooq.Table<?>> getTables0() {
		return java.util.Arrays.<org.jooq.Table<?>>asList(
			org.wikapidia.core.jooq.tables.Article.ARTICLE,
			org.wikapidia.core.jooq.tables.Link.LINK,
			org.wikapidia.core.jooq.tables.Concept.CONCEPT,
			org.wikapidia.core.jooq.tables.ArticleConcept.ARTICLE_CONCEPT,
			org.wikapidia.core.jooq.tables.LocalPage.LOCAL_PAGE);
	}
}
