/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package no.nav.pam.stilling.ext.avro;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

@org.apache.avro.specific.AvroGenerated
public class Location extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -8502257616915022922L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Location\",\"namespace\":\"no.nav.pam.stilling.ext.avro\",\"fields\":[{\"name\":\"address\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}],\"default\":null},{\"name\":\"postalCode\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}],\"default\":null},{\"name\":\"county\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}],\"default\":null},{\"name\":\"municipal\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}],\"default\":null},{\"name\":\"city\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}],\"default\":null},{\"name\":\"country\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"latitude\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}]},{\"name\":\"longitude\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}]},{\"name\":\"municipal_code\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}],\"default\":null},{\"name\":\"county_code\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}],\"default\":null}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<Location> ENCODER =
      new BinaryMessageEncoder<Location>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<Location> DECODER =
      new BinaryMessageDecoder<Location>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<Location> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<Location> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<Location> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<Location>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this Location to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a Location from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a Location instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static Location fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  @Deprecated public String address;
  @Deprecated public String postalCode;
  @Deprecated public String county;
  @Deprecated public String municipal;
  @Deprecated public String city;
  @Deprecated public String country;
  @Deprecated public String latitude;
  @Deprecated public String longitude;
  @Deprecated public String municipal_code;
  @Deprecated public String county_code;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public Location() {}

  /**
   * All-args constructor.
   * @param address The new value for address
   * @param postalCode The new value for postalCode
   * @param county The new value for county
   * @param municipal The new value for municipal
   * @param city The new value for city
   * @param country The new value for country
   * @param latitude The new value for latitude
   * @param longitude The new value for longitude
   * @param municipal_code The new value for municipal_code
   * @param county_code The new value for county_code
   */
  public Location(String address, String postalCode, String county, String municipal, String city, String country, String latitude, String longitude, String municipal_code, String county_code) {
    this.address = address;
    this.postalCode = postalCode;
    this.county = county;
    this.municipal = municipal;
    this.city = city;
    this.country = country;
    this.latitude = latitude;
    this.longitude = longitude;
    this.municipal_code = municipal_code;
    this.county_code = county_code;
  }

  public SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public Object get(int field$) {
    switch (field$) {
    case 0: return address;
    case 1: return postalCode;
    case 2: return county;
    case 3: return municipal;
    case 4: return city;
    case 5: return country;
    case 6: return latitude;
    case 7: return longitude;
    case 8: return municipal_code;
    case 9: return county_code;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, Object value$) {
    switch (field$) {
    case 0: address = value$ != null ? value$.toString() : null; break;
    case 1: postalCode = value$ != null ? value$.toString() : null; break;
    case 2: county = value$ != null ? value$.toString() : null; break;
    case 3: municipal = value$ != null ? value$.toString() : null; break;
    case 4: city = value$ != null ? value$.toString() : null; break;
    case 5: country = value$ != null ? value$.toString() : null; break;
    case 6: latitude = value$ != null ? value$.toString() : null; break;
    case 7: longitude = value$ != null ? value$.toString() : null; break;
    case 8: municipal_code = value$ != null ? value$.toString() : null; break;
    case 9: county_code = value$ != null ? value$.toString() : null; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'address' field.
   * @return The value of the 'address' field.
   */
  public String getAddress() {
    return address;
  }


  /**
   * Sets the value of the 'address' field.
   * @param value the value to set.
   */
  public void setAddress(String value) {
    this.address = value;
  }

  /**
   * Gets the value of the 'postalCode' field.
   * @return The value of the 'postalCode' field.
   */
  public String getPostalCode() {
    return postalCode;
  }


  /**
   * Sets the value of the 'postalCode' field.
   * @param value the value to set.
   */
  public void setPostalCode(String value) {
    this.postalCode = value;
  }

  /**
   * Gets the value of the 'county' field.
   * @return The value of the 'county' field.
   */
  public String getCounty() {
    return county;
  }


  /**
   * Sets the value of the 'county' field.
   * @param value the value to set.
   */
  public void setCounty(String value) {
    this.county = value;
  }

  /**
   * Gets the value of the 'municipal' field.
   * @return The value of the 'municipal' field.
   */
  public String getMunicipal() {
    return municipal;
  }


  /**
   * Sets the value of the 'municipal' field.
   * @param value the value to set.
   */
  public void setMunicipal(String value) {
    this.municipal = value;
  }

  /**
   * Gets the value of the 'city' field.
   * @return The value of the 'city' field.
   */
  public String getCity() {
    return city;
  }


  /**
   * Sets the value of the 'city' field.
   * @param value the value to set.
   */
  public void setCity(String value) {
    this.city = value;
  }

  /**
   * Gets the value of the 'country' field.
   * @return The value of the 'country' field.
   */
  public String getCountry() {
    return country;
  }


  /**
   * Sets the value of the 'country' field.
   * @param value the value to set.
   */
  public void setCountry(String value) {
    this.country = value;
  }

  /**
   * Gets the value of the 'latitude' field.
   * @return The value of the 'latitude' field.
   */
  public String getLatitude() {
    return latitude;
  }


  /**
   * Sets the value of the 'latitude' field.
   * @param value the value to set.
   */
  public void setLatitude(String value) {
    this.latitude = value;
  }

  /**
   * Gets the value of the 'longitude' field.
   * @return The value of the 'longitude' field.
   */
  public String getLongitude() {
    return longitude;
  }


  /**
   * Sets the value of the 'longitude' field.
   * @param value the value to set.
   */
  public void setLongitude(String value) {
    this.longitude = value;
  }

  /**
   * Gets the value of the 'municipal_code' field.
   * @return The value of the 'municipal_code' field.
   */
  public String getMunicipalCode() {
    return municipal_code;
  }


  /**
   * Sets the value of the 'municipal_code' field.
   * @param value the value to set.
   */
  public void setMunicipalCode(String value) {
    this.municipal_code = value;
  }

  /**
   * Gets the value of the 'county_code' field.
   * @return The value of the 'county_code' field.
   */
  public String getCountyCode() {
    return county_code;
  }


  /**
   * Sets the value of the 'county_code' field.
   * @param value the value to set.
   */
  public void setCountyCode(String value) {
    this.county_code = value;
  }

  /**
   * Creates a new Location RecordBuilder.
   * @return A new Location RecordBuilder
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Creates a new Location RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new Location RecordBuilder
   */
  public static Builder newBuilder(Builder other) {
    if (other == null) {
      return new Builder();
    } else {
      return new Builder(other);
    }
  }

  /**
   * Creates a new Location RecordBuilder by copying an existing Location instance.
   * @param other The existing instance to copy.
   * @return A new Location RecordBuilder
   */
  public static Builder newBuilder(Location other) {
    if (other == null) {
      return new Builder();
    } else {
      return new Builder(other);
    }
  }

  /**
   * RecordBuilder for Location instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<Location>
    implements org.apache.avro.data.RecordBuilder<Location> {

    private String address;
    private String postalCode;
    private String county;
    private String municipal;
    private String city;
    private String country;
    private String latitude;
    private String longitude;
    private String municipal_code;
    private String county_code;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.address)) {
        this.address = data().deepCopy(fields()[0].schema(), other.address);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.postalCode)) {
        this.postalCode = data().deepCopy(fields()[1].schema(), other.postalCode);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (isValidValue(fields()[2], other.county)) {
        this.county = data().deepCopy(fields()[2].schema(), other.county);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
      if (isValidValue(fields()[3], other.municipal)) {
        this.municipal = data().deepCopy(fields()[3].schema(), other.municipal);
        fieldSetFlags()[3] = other.fieldSetFlags()[3];
      }
      if (isValidValue(fields()[4], other.city)) {
        this.city = data().deepCopy(fields()[4].schema(), other.city);
        fieldSetFlags()[4] = other.fieldSetFlags()[4];
      }
      if (isValidValue(fields()[5], other.country)) {
        this.country = data().deepCopy(fields()[5].schema(), other.country);
        fieldSetFlags()[5] = other.fieldSetFlags()[5];
      }
      if (isValidValue(fields()[6], other.latitude)) {
        this.latitude = data().deepCopy(fields()[6].schema(), other.latitude);
        fieldSetFlags()[6] = other.fieldSetFlags()[6];
      }
      if (isValidValue(fields()[7], other.longitude)) {
        this.longitude = data().deepCopy(fields()[7].schema(), other.longitude);
        fieldSetFlags()[7] = other.fieldSetFlags()[7];
      }
      if (isValidValue(fields()[8], other.municipal_code)) {
        this.municipal_code = data().deepCopy(fields()[8].schema(), other.municipal_code);
        fieldSetFlags()[8] = other.fieldSetFlags()[8];
      }
      if (isValidValue(fields()[9], other.county_code)) {
        this.county_code = data().deepCopy(fields()[9].schema(), other.county_code);
        fieldSetFlags()[9] = other.fieldSetFlags()[9];
      }
    }

    /**
     * Creates a Builder by copying an existing Location instance
     * @param other The existing instance to copy.
     */
    private Builder(Location other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.address)) {
        this.address = data().deepCopy(fields()[0].schema(), other.address);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.postalCode)) {
        this.postalCode = data().deepCopy(fields()[1].schema(), other.postalCode);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.county)) {
        this.county = data().deepCopy(fields()[2].schema(), other.county);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.municipal)) {
        this.municipal = data().deepCopy(fields()[3].schema(), other.municipal);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.city)) {
        this.city = data().deepCopy(fields()[4].schema(), other.city);
        fieldSetFlags()[4] = true;
      }
      if (isValidValue(fields()[5], other.country)) {
        this.country = data().deepCopy(fields()[5].schema(), other.country);
        fieldSetFlags()[5] = true;
      }
      if (isValidValue(fields()[6], other.latitude)) {
        this.latitude = data().deepCopy(fields()[6].schema(), other.latitude);
        fieldSetFlags()[6] = true;
      }
      if (isValidValue(fields()[7], other.longitude)) {
        this.longitude = data().deepCopy(fields()[7].schema(), other.longitude);
        fieldSetFlags()[7] = true;
      }
      if (isValidValue(fields()[8], other.municipal_code)) {
        this.municipal_code = data().deepCopy(fields()[8].schema(), other.municipal_code);
        fieldSetFlags()[8] = true;
      }
      if (isValidValue(fields()[9], other.county_code)) {
        this.county_code = data().deepCopy(fields()[9].schema(), other.county_code);
        fieldSetFlags()[9] = true;
      }
    }

    /**
      * Gets the value of the 'address' field.
      * @return The value.
      */
    public String getAddress() {
      return address;
    }


    /**
      * Sets the value of the 'address' field.
      * @param value The value of 'address'.
      * @return This builder.
      */
    public Builder setAddress(String value) {
      validate(fields()[0], value);
      this.address = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'address' field has been set.
      * @return True if the 'address' field has been set, false otherwise.
      */
    public boolean hasAddress() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'address' field.
      * @return This builder.
      */
    public Builder clearAddress() {
      address = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'postalCode' field.
      * @return The value.
      */
    public String getPostalCode() {
      return postalCode;
    }


    /**
      * Sets the value of the 'postalCode' field.
      * @param value The value of 'postalCode'.
      * @return This builder.
      */
    public Builder setPostalCode(String value) {
      validate(fields()[1], value);
      this.postalCode = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'postalCode' field has been set.
      * @return True if the 'postalCode' field has been set, false otherwise.
      */
    public boolean hasPostalCode() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'postalCode' field.
      * @return This builder.
      */
    public Builder clearPostalCode() {
      postalCode = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'county' field.
      * @return The value.
      */
    public String getCounty() {
      return county;
    }


    /**
      * Sets the value of the 'county' field.
      * @param value The value of 'county'.
      * @return This builder.
      */
    public Builder setCounty(String value) {
      validate(fields()[2], value);
      this.county = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'county' field has been set.
      * @return True if the 'county' field has been set, false otherwise.
      */
    public boolean hasCounty() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'county' field.
      * @return This builder.
      */
    public Builder clearCounty() {
      county = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /**
      * Gets the value of the 'municipal' field.
      * @return The value.
      */
    public String getMunicipal() {
      return municipal;
    }


    /**
      * Sets the value of the 'municipal' field.
      * @param value The value of 'municipal'.
      * @return This builder.
      */
    public Builder setMunicipal(String value) {
      validate(fields()[3], value);
      this.municipal = value;
      fieldSetFlags()[3] = true;
      return this;
    }

    /**
      * Checks whether the 'municipal' field has been set.
      * @return True if the 'municipal' field has been set, false otherwise.
      */
    public boolean hasMunicipal() {
      return fieldSetFlags()[3];
    }


    /**
      * Clears the value of the 'municipal' field.
      * @return This builder.
      */
    public Builder clearMunicipal() {
      municipal = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    /**
      * Gets the value of the 'city' field.
      * @return The value.
      */
    public String getCity() {
      return city;
    }


    /**
      * Sets the value of the 'city' field.
      * @param value The value of 'city'.
      * @return This builder.
      */
    public Builder setCity(String value) {
      validate(fields()[4], value);
      this.city = value;
      fieldSetFlags()[4] = true;
      return this;
    }

    /**
      * Checks whether the 'city' field has been set.
      * @return True if the 'city' field has been set, false otherwise.
      */
    public boolean hasCity() {
      return fieldSetFlags()[4];
    }


    /**
      * Clears the value of the 'city' field.
      * @return This builder.
      */
    public Builder clearCity() {
      city = null;
      fieldSetFlags()[4] = false;
      return this;
    }

    /**
      * Gets the value of the 'country' field.
      * @return The value.
      */
    public String getCountry() {
      return country;
    }


    /**
      * Sets the value of the 'country' field.
      * @param value The value of 'country'.
      * @return This builder.
      */
    public Builder setCountry(String value) {
      validate(fields()[5], value);
      this.country = value;
      fieldSetFlags()[5] = true;
      return this;
    }

    /**
      * Checks whether the 'country' field has been set.
      * @return True if the 'country' field has been set, false otherwise.
      */
    public boolean hasCountry() {
      return fieldSetFlags()[5];
    }


    /**
      * Clears the value of the 'country' field.
      * @return This builder.
      */
    public Builder clearCountry() {
      country = null;
      fieldSetFlags()[5] = false;
      return this;
    }

    /**
      * Gets the value of the 'latitude' field.
      * @return The value.
      */
    public String getLatitude() {
      return latitude;
    }


    /**
      * Sets the value of the 'latitude' field.
      * @param value The value of 'latitude'.
      * @return This builder.
      */
    public Builder setLatitude(String value) {
      validate(fields()[6], value);
      this.latitude = value;
      fieldSetFlags()[6] = true;
      return this;
    }

    /**
      * Checks whether the 'latitude' field has been set.
      * @return True if the 'latitude' field has been set, false otherwise.
      */
    public boolean hasLatitude() {
      return fieldSetFlags()[6];
    }


    /**
      * Clears the value of the 'latitude' field.
      * @return This builder.
      */
    public Builder clearLatitude() {
      latitude = null;
      fieldSetFlags()[6] = false;
      return this;
    }

    /**
      * Gets the value of the 'longitude' field.
      * @return The value.
      */
    public String getLongitude() {
      return longitude;
    }


    /**
      * Sets the value of the 'longitude' field.
      * @param value The value of 'longitude'.
      * @return This builder.
      */
    public Builder setLongitude(String value) {
      validate(fields()[7], value);
      this.longitude = value;
      fieldSetFlags()[7] = true;
      return this;
    }

    /**
      * Checks whether the 'longitude' field has been set.
      * @return True if the 'longitude' field has been set, false otherwise.
      */
    public boolean hasLongitude() {
      return fieldSetFlags()[7];
    }


    /**
      * Clears the value of the 'longitude' field.
      * @return This builder.
      */
    public Builder clearLongitude() {
      longitude = null;
      fieldSetFlags()[7] = false;
      return this;
    }

    /**
      * Gets the value of the 'municipal_code' field.
      * @return The value.
      */
    public String getMunicipalCode() {
      return municipal_code;
    }


    /**
      * Sets the value of the 'municipal_code' field.
      * @param value The value of 'municipal_code'.
      * @return This builder.
      */
    public Builder setMunicipalCode(String value) {
      validate(fields()[8], value);
      this.municipal_code = value;
      fieldSetFlags()[8] = true;
      return this;
    }

    /**
      * Checks whether the 'municipal_code' field has been set.
      * @return True if the 'municipal_code' field has been set, false otherwise.
      */
    public boolean hasMunicipalCode() {
      return fieldSetFlags()[8];
    }


    /**
      * Clears the value of the 'municipal_code' field.
      * @return This builder.
      */
    public Builder clearMunicipalCode() {
      municipal_code = null;
      fieldSetFlags()[8] = false;
      return this;
    }

    /**
      * Gets the value of the 'county_code' field.
      * @return The value.
      */
    public String getCountyCode() {
      return county_code;
    }


    /**
      * Sets the value of the 'county_code' field.
      * @param value The value of 'county_code'.
      * @return This builder.
      */
    public Builder setCountyCode(String value) {
      validate(fields()[9], value);
      this.county_code = value;
      fieldSetFlags()[9] = true;
      return this;
    }

    /**
      * Checks whether the 'county_code' field has been set.
      * @return True if the 'county_code' field has been set, false otherwise.
      */
    public boolean hasCountyCode() {
      return fieldSetFlags()[9];
    }


    /**
      * Clears the value of the 'county_code' field.
      * @return This builder.
      */
    public Builder clearCountyCode() {
      county_code = null;
      fieldSetFlags()[9] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Location build() {
      try {
        Location record = new Location();
        record.address = fieldSetFlags()[0] ? this.address : (String) defaultValue(fields()[0]);
        record.postalCode = fieldSetFlags()[1] ? this.postalCode : (String) defaultValue(fields()[1]);
        record.county = fieldSetFlags()[2] ? this.county : (String) defaultValue(fields()[2]);
        record.municipal = fieldSetFlags()[3] ? this.municipal : (String) defaultValue(fields()[3]);
        record.city = fieldSetFlags()[4] ? this.city : (String) defaultValue(fields()[4]);
        record.country = fieldSetFlags()[5] ? this.country : (String) defaultValue(fields()[5]);
        record.latitude = fieldSetFlags()[6] ? this.latitude : (String) defaultValue(fields()[6]);
        record.longitude = fieldSetFlags()[7] ? this.longitude : (String) defaultValue(fields()[7]);
        record.municipal_code = fieldSetFlags()[8] ? this.municipal_code : (String) defaultValue(fields()[8]);
        record.county_code = fieldSetFlags()[9] ? this.county_code : (String) defaultValue(fields()[9]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<Location>
    WRITER$ = (org.apache.avro.io.DatumWriter<Location>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<Location>
    READER$ = (org.apache.avro.io.DatumReader<Location>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    if (this.address == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.address);
    }

    if (this.postalCode == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.postalCode);
    }

    if (this.county == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.county);
    }

    if (this.municipal == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.municipal);
    }

    if (this.city == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.city);
    }

    out.writeString(this.country);

    if (this.latitude == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.latitude);
    }

    if (this.longitude == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.longitude);
    }

    if (this.municipal_code == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.municipal_code);
    }

    if (this.county_code == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.county_code);
    }

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      if (in.readIndex() != 1) {
        in.readNull();
        this.address = null;
      } else {
        this.address = in.readString();
      }

      if (in.readIndex() != 1) {
        in.readNull();
        this.postalCode = null;
      } else {
        this.postalCode = in.readString();
      }

      if (in.readIndex() != 1) {
        in.readNull();
        this.county = null;
      } else {
        this.county = in.readString();
      }

      if (in.readIndex() != 1) {
        in.readNull();
        this.municipal = null;
      } else {
        this.municipal = in.readString();
      }

      if (in.readIndex() != 1) {
        in.readNull();
        this.city = null;
      } else {
        this.city = in.readString();
      }

      this.country = in.readString();

      if (in.readIndex() != 1) {
        in.readNull();
        this.latitude = null;
      } else {
        this.latitude = in.readString();
      }

      if (in.readIndex() != 1) {
        in.readNull();
        this.longitude = null;
      } else {
        this.longitude = in.readString();
      }

      if (in.readIndex() != 1) {
        in.readNull();
        this.municipal_code = null;
      } else {
        this.municipal_code = in.readString();
      }

      if (in.readIndex() != 1) {
        in.readNull();
        this.county_code = null;
      } else {
        this.county_code = in.readString();
      }

    } else {
      for (int i = 0; i < 10; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          if (in.readIndex() != 1) {
            in.readNull();
            this.address = null;
          } else {
            this.address = in.readString();
          }
          break;

        case 1:
          if (in.readIndex() != 1) {
            in.readNull();
            this.postalCode = null;
          } else {
            this.postalCode = in.readString();
          }
          break;

        case 2:
          if (in.readIndex() != 1) {
            in.readNull();
            this.county = null;
          } else {
            this.county = in.readString();
          }
          break;

        case 3:
          if (in.readIndex() != 1) {
            in.readNull();
            this.municipal = null;
          } else {
            this.municipal = in.readString();
          }
          break;

        case 4:
          if (in.readIndex() != 1) {
            in.readNull();
            this.city = null;
          } else {
            this.city = in.readString();
          }
          break;

        case 5:
          this.country = in.readString();
          break;

        case 6:
          if (in.readIndex() != 1) {
            in.readNull();
            this.latitude = null;
          } else {
            this.latitude = in.readString();
          }
          break;

        case 7:
          if (in.readIndex() != 1) {
            in.readNull();
            this.longitude = null;
          } else {
            this.longitude = in.readString();
          }
          break;

        case 8:
          if (in.readIndex() != 1) {
            in.readNull();
            this.municipal_code = null;
          } else {
            this.municipal_code = in.readString();
          }
          break;

        case 9:
          if (in.readIndex() != 1) {
            in.readNull();
            this.county_code = null;
          } else {
            this.county_code = in.readString();
          }
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










