package c3.ops.priam;

import c3.ops.priam.identity.IPriamInstanceFactory;
import c3.ops.priam.identity.PriamInstance;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.util.*;

public class FakePriamInstanceFactory implements IPriamInstanceFactory<PriamInstance> {
  private final Map<Integer, PriamInstance> instances = Maps.newHashMap();
  private final IConfiguration config;

  @Inject
  public FakePriamInstanceFactory(IConfiguration config) {
    this.config = config;
  }

  @Override
  public List<PriamInstance> getAllIds(String appName) {
    return new ArrayList<PriamInstance>(instances.values());
  }

  @Override
  public PriamInstance getInstance(String appName, String dc, int id) {
    return instances.get(id);
  }

  @Override
  public PriamInstance create(String app, int id, String instanceID, String hostname, String ip, String rac, Map<String, Object> volumes, String payload) {
    PriamInstance ins = new PriamInstance();
    ins.setApp(app);
    ins.setRac(rac);
    ins.setHost(hostname, ip);
    ins.setId(id);
    ins.setInstanceId(instanceID);
    ins.setToken(payload);
    ins.setVolumes(volumes);
    ins.setDC(config.getDC());
    instances.put(id, ins);
    return ins;
  }

  @Override
  public void delete(PriamInstance inst) {
    instances.remove(inst.getId());
  }

  @Override
  public void update(PriamInstance inst) {
    instances.put(inst.getId(), inst);
  }


  @Override
  public void sort(List<PriamInstance> return_) {
    Comparator<? super PriamInstance> comparator = new Comparator<PriamInstance>() {

      @Override
      public int compare(PriamInstance o1, PriamInstance o2) {
        Integer c1 = o1.getId();
        Integer c2 = o2.getId();
        return c1.compareTo(c2);
      }
    };
    Collections.sort(return_, comparator);
  }

  @Override
  public void attachVolumes(PriamInstance instance, String mountPath, String device) {
    // TODO Auto-generated method stub
  }


}
