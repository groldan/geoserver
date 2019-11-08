/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.config.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.ConverterMatcher;
import com.thoughtworks.xstream.converters.ConverterRegistry;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.basic.BigDecimalConverter;
import com.thoughtworks.xstream.converters.basic.BigIntegerConverter;
import com.thoughtworks.xstream.converters.basic.BooleanConverter;
import com.thoughtworks.xstream.converters.basic.ByteConverter;
import com.thoughtworks.xstream.converters.basic.CharConverter;
import com.thoughtworks.xstream.converters.basic.DateConverter;
import com.thoughtworks.xstream.converters.basic.DoubleConverter;
import com.thoughtworks.xstream.converters.basic.FloatConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.converters.basic.LongConverter;
import com.thoughtworks.xstream.converters.basic.NullConverter;
import com.thoughtworks.xstream.converters.basic.ShortConverter;
import com.thoughtworks.xstream.converters.basic.StringBufferConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.converters.basic.URIConverter;
import com.thoughtworks.xstream.converters.basic.URLConverter;
import com.thoughtworks.xstream.converters.collections.ArrayConverter;
import com.thoughtworks.xstream.converters.collections.BitSetConverter;
import com.thoughtworks.xstream.converters.collections.CharArrayConverter;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.converters.collections.SingletonCollectionConverter;
import com.thoughtworks.xstream.converters.collections.SingletonMapConverter;
import com.thoughtworks.xstream.converters.extended.ColorConverter;
import com.thoughtworks.xstream.converters.extended.EncodedByteArrayConverter;
import com.thoughtworks.xstream.converters.extended.FileConverter;
import com.thoughtworks.xstream.converters.extended.GregorianCalendarConverter;
import com.thoughtworks.xstream.converters.extended.JavaClassConverter;
import com.thoughtworks.xstream.converters.extended.JavaFieldConverter;
import com.thoughtworks.xstream.converters.extended.JavaMethodConverter;
import com.thoughtworks.xstream.converters.extended.LocaleConverter;
import com.thoughtworks.xstream.converters.extended.SqlDateConverter;
import com.thoughtworks.xstream.converters.extended.SqlTimeConverter;
import com.thoughtworks.xstream.converters.extended.SqlTimestampConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.util.SelfStreamingInstanceChecker;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import com.thoughtworks.xstream.security.ForbiddenClassException;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.util.NumberRange;
import org.geotools.util.SimpleInternationalString;
import org.geotools.util.Version;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * A XStream subclass allowing conversion of no class other than those explicitly registered using
 * the allowType* methods. To simplify the setup, it already allows the use of primitives, strings,
 * dates and collections
 *
 * @author Andrea Aime - GeoSolutions
 */
public class SecureXStream extends XStream {
    private static final String WHITELIST_KEY = "GEOSERVER_XSTREAM_WHITELIST";

    static final Logger LOGGER = Logging.getLogger(SecureXStream.class);

    public SecureXStream() {
        super();
        init();
    }

    public SecureXStream(HierarchicalStreamDriver hierarchicalStreamDriver) {
        super(hierarchicalStreamDriver);
        init();
    }

    public SecureXStream(
            ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver,
            ClassLoaderReference classLoaderReference,
            Mapper mapper,
            ConverterLookup converterLookup,
            ConverterRegistry converterRegistry) {
        super(
                reflectionProvider,
                driver,
                classLoaderReference,
                mapper,
                converterLookup,
                converterRegistry);
        init();
    }

    public SecureXStream(
            ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver,
            ClassLoaderReference classLoaderReference,
            Mapper mapper) {
        super(reflectionProvider, driver, classLoaderReference, mapper);
        init();
    }

    public SecureXStream(
            ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver driver,
            ClassLoaderReference classLoaderReference) {
        super(reflectionProvider, driver, classLoaderReference);
        init();
    }

    public SecureXStream(
            ReflectionProvider reflectionProvider,
            HierarchicalStreamDriver hierarchicalStreamDriver) {
        super(reflectionProvider, hierarchicalStreamDriver);
        init();
    }

    public SecureXStream(ReflectionProvider reflectionProvider) {
        super(reflectionProvider);
        init();
    }

    private void init() {
        // by default, convert nothing
        addPermission(NoTypePermission.NONE);

        // the placeholder for null values
        allowTypes(new Class[] {Mapper.Null.class});
        // allow primitives
        addPermission(new PrimitiveTypePermission());
        // and common non primitives
        allowTypes(
                new Class[] {
                    String.class, Date.class, java.sql.Date.class, Timestamp.class, Time.class
                });
        // allow common GeoTools types too
        allowTypeHierarchy(Filter.class);
        allowTypeHierarchy(NumberRange.class);
        allowTypeHierarchy(CoordinateReferenceSystem.class);
        allowTypeHierarchy(Name.class);
        allowTypes(new Class[] {Version.class, SimpleInternationalString.class});
        // common collection types
        allowTypes(
                new Class[] {
                    TreeSet.class,
                    SortedSet.class,
                    Set.class,
                    HashSet.class,
                    LinkedHashSet.class,
                    List.class,
                    ArrayList.class,
                    CopyOnWriteArrayList.class,
                    Map.class,
                    HashMap.class,
                    TreeMap.class,
                    ConcurrentHashMap.class,
                });

        // Allow classes from user defined whitelist
        String whitelistProp = GeoServerExtensions.getProperty(WHITELIST_KEY);
        if (whitelistProp != null) {
            String[] wildcards = whitelistProp.split("\\s+|(\\s*;\\s*)");
            this.allowTypesByWildcard(wildcards);
        }
    }

    @Override
    protected MapperWrapper wrapMapper(MapperWrapper next) {
        return new DetailedSecurityExceptionWrapper(next);
    }

    /**
     * This method is a clone of the base class one, leaving the converters in the same order where
     * possible, but altering a few ones performing illegal reflective accesses against Java core
     * classes, replacing them with alternatives that do not, or simply removing them, if we are not
     * using them
     */
    @Override
    protected void setupConverters() {
        // register the converters that don't need any context and hence can be cached
        getCachedConverters()
                .forEach(
                        cm -> {
                            if (cm instanceof Converter) {
                                registerConverter((Converter) cm);
                            } else if (cm instanceof SingleValueConverter) {
                                registerConverter((SingleValueConverter) cm);
                            } else {
                                throw new IllegalStateException();
                            }
                        });
        if (JVM.isAWTAvailable()) {
            registerConverter(new ColorConverter());
        }

        // register the converters that need some specific instance context
        Mapper mapper = getMapper();
        ReflectionProvider reflectionProvider = getReflectionProvider();
        ClassLoaderReference classLoaderReference = getClassLoaderReference();
        ConverterLookup converterLookup = getConverterLookup();

        // we really shouldn't need this one...
        // if (JVM.isSwingAvailable()) {
        //    registerConverter(new LookAndFeelConverter(mapper, reflectionProvider));
        // }
        registerConverter(new ReflectionConverter(mapper, reflectionProvider), PRIORITY_VERY_LOW);
        registerConverter(new ArrayConverter(mapper));
        registerConverter(new CharArrayConverter());
        registerConverter(new CollectionConverter(mapper));
        registerConverter(new MapConverter(mapper));
        registerConverter(new TreeMapConverter(mapper));
        registerConverter(new TreeSetConverter(mapper));
        registerConverter(new SingletonCollectionConverter(mapper));
        registerConverter(new SingletonMapConverter(mapper));
        registerConverter(new JavaClassConverter(classLoaderReference));
        registerConverter(new JavaMethodConverter(classLoaderReference));
        registerConverter(new JavaFieldConverter(classLoaderReference));
        registerConverter(
                new com.thoughtworks.xstream.converters.extended.SubjectConverter(mapper));
        registerConverter(
                new com.thoughtworks.xstream.converters.extended.ThrowableConverter(
                        converterLookup));
        registerConverter(
                new com.thoughtworks.xstream.converters.time.SystemClockConverter(mapper));
        registerConverter(new com.thoughtworks.xstream.converters.time.ValueRangeConverter(mapper));
        registerConverter(new com.thoughtworks.xstream.converters.time.WeekFieldsConverter(mapper));
        registerConverter(
                new com.thoughtworks.xstream.converters.reflection.LambdaConverter(
                        mapper, reflectionProvider, classLoaderReference));
        registerConverter(new SelfStreamingInstanceChecker(converterLookup, this));
    }
    /**
     * A wrapper that adds instructions on what to do when a class was not part of the whitelist
     *
     * @author Andrea Aime - GeoSolutions
     */
    static class DetailedSecurityExceptionWrapper extends MapperWrapper {

        public DetailedSecurityExceptionWrapper(Mapper wrapped) {
            super(wrapped);
        }

        @Override
        public Class realClass(String elementName) {
            try {
                return super.realClass(elementName);
            } catch (ForbiddenClassException e) {
                StringBuilder sb = new StringBuilder();
                sb.append("Class {0} is not whitelisted for XML parsing. \n");
                sb.append(
                                "This is done to prevent Remote Code Execution attacks, but it might be \n")
                        .append(
                                "you need this class to be authorized for GeoServer to actually work\n");
                sb.append("If you are a user, you can set a variable named ")
                        .append(WHITELIST_KEY)
                        .append("\n")
                        .append(
                                "  with a semicolon separated list of fully qualified names, or patterns\n")
                        .append(
                                "  to match several classes.The variable can be set as a system variable,\n")
                        .append(
                                "  an environment variable, or a servlet context variable, just like\n")
                        .append("  GEOSERVER_DATA_DIR.\n")
                        .append(
                                "  For example, in order to authorize the org.geoserver.Foo class,\n")
                        .append(
                                "  plus any class in the org.geoserver.custom package, one could set\n")
                        .append("  a system variable: \n")
                        .append("  -D")
                        .append(WHITELIST_KEY)
                        .append("=org.geoserver.Foo;org.geoserver.custom.**\n");
                sb.append(
                                "If instead you are a developer, you can call allowTypes/allowTypeHierarchy against\n")
                        .append("  the XStream used for serialization by rolling a custom\n")
                        .append(
                                "  XStreamPersisterInitializer or customizing your XStreamServiceLoader.");
                LOGGER.log(Level.SEVERE, sb.toString(), e.getMessage());

                throw new ForbiddenClassExceptionEx(
                        "Unauthorized class found, see logs for more details on how to handle it: "
                                + e.getMessage(),
                        e);
            }
        }
    }

    /**
     * Just to have a recognizable class for tests
     *
     * @author Andrea Aime - GeoSolutions
     */
    static class ForbiddenClassExceptionEx extends RuntimeException {

        public ForbiddenClassExceptionEx(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static volatile List<ConverterMatcher> cachedConverters;

    private static List<ConverterMatcher> getCachedConverters() {
        if (cachedConverters == null) {
            synchronized (SecureXStream.class) {
                if (cachedConverters == null) {
                    cachedConverters = loadCachedConverters();
                }
            }
        }
        return cachedConverters;
    }

    private static List<ConverterMatcher> loadCachedConverters() {
        return Arrays.asList(
                new NullConverter(),
                new IntConverter(),
                new FloatConverter(),
                new DoubleConverter(),
                new LongConverter(),
                new ShortConverter(),
                (Converter) new CharConverter(),
                new BooleanConverter(),
                new ByteConverter(),
                new StringConverter(),
                new StringBufferConverter(),
                new DateConverter(),
                new BitSetConverter(),
                new URIConverter(),
                new URLConverter(),
                new BigIntegerConverter(),
                new BigDecimalConverter(),
                (Converter) new EncodedByteArrayConverter(),
                new FileConverter(),
                new SqlTimestampConverter(),
                new SqlTimeConverter(),
                new SqlDateConverter(),
                new LocaleConverter(),
                new GregorianCalendarConverter(),
                // late bound converters - allows XStream to be compiled on earlier JDKs
                new com.thoughtworks.xstream.converters.extended.StackTraceElementConverter(),
                new com.thoughtworks.xstream.converters.extended.CurrencyConverter(),
                new com.thoughtworks.xstream.converters.extended.RegexPatternConverter(),
                new com.thoughtworks.xstream.converters.extended.CharsetConverter(),
                new com.thoughtworks.xstream.converters.extended.DurationConverter(),
                new com.thoughtworks.xstream.converters.enums.EnumConverter(),
                new com.thoughtworks.xstream.converters.basic.StringBuilderConverter(),
                new com.thoughtworks.xstream.converters.basic.UUIDConverter(),
                new com.thoughtworks.xstream.converters.extended.ActivationDataFlavorConverter(),
                new com.thoughtworks.xstream.converters.extended.PathConverter(),
                new com.thoughtworks.xstream.converters.time.ChronologyConverter(),
                new com.thoughtworks.xstream.converters.time.DurationConverter(),
                new com.thoughtworks.xstream.converters.time.HijrahDateConverter(),
                new com.thoughtworks.xstream.converters.time.JapaneseDateConverter(),
                new com.thoughtworks.xstream.converters.time.JapaneseEraConverter(),
                new com.thoughtworks.xstream.converters.time.LocalDateConverter(),
                new com.thoughtworks.xstream.converters.time.InstantConverter(),
                new com.thoughtworks.xstream.converters.time.LocalDateTimeConverter(),
                new com.thoughtworks.xstream.converters.time.LocalTimeConverter(),
                new com.thoughtworks.xstream.converters.time.MinguoDateConverter(),
                new com.thoughtworks.xstream.converters.time.MonthDayConverter(),
                new com.thoughtworks.xstream.converters.time.OffsetDateTimeConverter(),
                new com.thoughtworks.xstream.converters.time.OffsetTimeConverter(),
                new com.thoughtworks.xstream.converters.time.PeriodConverter(),
                new com.thoughtworks.xstream.converters.time.ThaiBuddhistDateConverter(),
                new com.thoughtworks.xstream.converters.time.YearConverter(),
                new com.thoughtworks.xstream.converters.time.YearMonthConverter(),
                new com.thoughtworks.xstream.converters.time.ZonedDateTimeConverter(),
                new com.thoughtworks.xstream.converters.time.ZoneIdConverter());
    }
}
