/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package no.nav.pam.stilling.ext.avro;
@org.apache.avro.specific.AvroGenerated
public enum RemarkType implements org.apache.avro.generic.GenericEnumSymbol<RemarkType> {
  NOT_APPROVED_BY_LABOUR_INSPECTION, NO_EMPLOYMENT, DUPLICATE, DISCRIMINATING, REJECT_BECAUSE_CAPACITY, FOREIGN_JOB, COLLECTION_JOB, UNKNOWN  ;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"enum\",\"name\":\"RemarkType\",\"namespace\":\"no.nav.pam.stilling.ext.avro\",\"symbols\":[\"NOT_APPROVED_BY_LABOUR_INSPECTION\",\"NO_EMPLOYMENT\",\"DUPLICATE\",\"DISCRIMINATING\",\"REJECT_BECAUSE_CAPACITY\",\"FOREIGN_JOB\",\"COLLECTION_JOB\",\"UNKNOWN\"]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
}
