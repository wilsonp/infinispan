package org.infinispan.query.indexmanager;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.search.backend.LuceneWork;
import org.hibernate.search.spi.SearchIntegrator;
import org.infinispan.Cache;
import org.infinispan.commands.ReplicableCommand;
import org.infinispan.commands.remote.BaseRpcCommand;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.marshall.MarshallUtil;
import org.infinispan.context.InvocationContext;
import org.infinispan.query.Search;
import org.infinispan.query.SearchManager;
import org.infinispan.query.backend.KeyTransformationHandler;
import org.infinispan.query.backend.QueryInterceptor;
import org.infinispan.query.impl.CommandInitializer;
import org.infinispan.query.impl.ComponentRegistryUtils;
import org.infinispan.query.impl.CustomQueryCommand;
import org.infinispan.query.logging.Log;
import org.infinispan.util.ByteString;
import org.infinispan.util.logging.LogFactory;

/**
 * Base class for index commands
 *
 * @author gustavonalle
 * @since 7.0
 */
public abstract class AbstractUpdateCommand extends BaseRpcCommand implements ReplicableCommand, CustomQueryCommand {

   protected static final Log log = LogFactory.getLog(AbstractUpdateCommand.class, Log.class);

   protected SearchIntegrator searchFactory;
   protected String indexName;
   protected byte[] serializedModel;
   protected QueryInterceptor queryInterceptor;

   protected AbstractUpdateCommand(ByteString cacheName) {
      super(cacheName);
   }

   @Override
   public abstract Object perform(InvocationContext ctx) throws Throwable;

   @Override
   public abstract byte getCommandId();

   @Override
   public void writeTo(ObjectOutput output) throws IOException {
      if (indexName == null) {
         output.writeBoolean(false);
      } else {
         output.writeBoolean(true);
         output.writeUTF(indexName);
      }
      MarshallUtil.marshallByteArray(serializedModel, output);
   }

   @Override
   public void readFrom(ObjectInput input) throws IOException, ClassNotFoundException {
      boolean hasIndexName = input.readBoolean();
      if (hasIndexName) {
         indexName = input.readUTF();
      }
      serializedModel = MarshallUtil.unmarshallByteArray(input);
   }

   @Override
   public boolean isReturnValueExpected() {
      return false;
   }

   /**
    * This is invoked only on the receiving node, before {@link #perform(InvocationContext)}
    */
   @Override
   public void fetchExecutionContext(CommandInitializer ci) {
      String name = cacheName.toString();
      if (ci.getCacheManager().cacheExists(name)) {
         Cache cache = ci.getCacheManager().getCache(name);
         SearchManager searchManager = Search.getSearchManager(cache);
         searchFactory = searchManager.unwrap(SearchIntegrator.class);
         queryInterceptor = ComponentRegistryUtils.getQueryInterceptor(cache);
      } else {
         throw new CacheException("Cache named '" + name + "' does not exist on this CacheManager, or was not started");
      }
   }

   @Override
   public boolean canBlock() {
      return true;
   }

   public String getIndexName() {
      return indexName;
   }

   protected List<LuceneWork> transformKeysToStrings(final List<LuceneWork> luceneWorks) {
      ArrayList<LuceneWork> transformedWorks = new ArrayList<>(luceneWorks.size());
      for (LuceneWork lw : luceneWorks) {
         transformedWorks.add(transformKeyToStrings(lw));
      }
      return transformedWorks;
   }

   protected LuceneWork transformKeyToStrings(final LuceneWork luceneWork) {
      final KeyTransformationHandler keyTransformationHandler = queryInterceptor.getKeyTransformationHandler();
      return luceneWork.acceptIndexWorkVisitor(LuceneWorkTransformationVisitor.INSTANCE, keyTransformationHandler);
   }


   protected void setSerializedWorkList(byte[] serializedModel) {
      this.serializedModel = serializedModel;
   }

   protected void setIndexName(String indexName) {
      this.indexName = indexName;
   }
}
