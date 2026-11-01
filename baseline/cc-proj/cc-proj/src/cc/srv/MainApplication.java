package cc.srv;

import java.util.HashSet;
import java.util.Set;

import cc.utils.GenericExceptionMapper;
import jakarta.ws.rs.core.Application;

public class MainApplication extends Application {

	private final Set<Object> singletons = new HashSet<>();
	private final Set<Class<?>> resources = new HashSet<>();

	public MainApplication() {
		resources.add(ControlResource.class);
		resources.add(UsersResource.class);
		resources.add(MediaResource.class);
		resources.add(AuctionsResource.class);
		resources.add(BidsResource.class);
		resources.add(QuestionsResource.class);
		resources.add(GenericExceptionMapper.class);
	}

	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}
