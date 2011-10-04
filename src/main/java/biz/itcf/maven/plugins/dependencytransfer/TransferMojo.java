package biz.itcf.maven.plugins.dependencytransfer;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.RequestTrace;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.deployment.DeploymentException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.RemoteRepositoryManager;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.LocalRepositoryManager;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.SubArtifact;

/**
 * <p>
 * </p>
 * 
 * @author Florian Fray (last modified by $Author: $)
 * @version $Revision: $ $Date: $
 * 
 * @goal transfer
 */
public class TransferMojo extends AbstractMojo {

    /**
     * @component
     */
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     * 
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     * 
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    private List<RemoteRepository> projectRepos;

    /**
     * @component
     */
    private ModelBuilder modelBuilder;

    /**
     * @component
     */
    private ArtifactResolver artifactResolver;

    /**
     * @component
     */
    private RemoteRepositoryManager remoteRepositoryManager;

    /**
     * @parameter expression="${groupId}"
     * @required
     */
    private String groupId;

    /**
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * @parameter expression="${version}"
     * @required
     */
    private String version;
    
    /**
     * @parameter expression="${classifier}"
     */
    private String classifier;

    /**
     * @parameter expression="${extension}"
     */
    private String extension;
    
    /**
     * @parameter expression="${targetRepositoryUrl}"
     * @required
     */
    private String targetRepositoryUrl;

    /**
     * @parameter expression="${user}"
     */
    private String username;

    /**
     * @parameter expression="${password}
     */
    private String password;

    static final class FileUtils {

        private FileUtils() {
        }

        public static void delete(File f) {
            if (f.isFile()) {
                f.delete();
            } else {
                // Stack<File> s = new Stack<File>();
                // s.push(f);
                // while (!s.isEmpty()) {
                // File dir = s.peek();
                // File[] containedFiles = dir.listFiles();
                // boolean isEmpty = true;
                // for (File fSub : containedFiles) {
                // if (fSub.isFile()) {
                // fSub.delete();
                // } else {
                // isEmpty = false;
                // s.push(fSub);
                // }
                // }
                // if (isEmpty) {
                // dir.delete();
                // s.pop();
                // }
                // }
                for (File sub : f.listFiles()) {
                    delete(sub);
                }
                f.delete();
            }
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
//        String basedir = "/Users/Florian/transfer";
//
//        File basedirFile = new File(basedir);
//        if (basedirFile.exists()) {
//            if (!basedirFile.isDirectory()) {
//                throw new MojoFailureException("File " + basedir + " is not a directory");
//            } else {
//                FileUtils.delete(basedirFile);
//            }
//        }
//        basedirFile.mkdir();

        Artifact rootArtifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(rootArtifact);
        artifactRequest.setRepositories(projectRepos);

        RemoteRepository targetRepository = new RemoteRepository(null, "default", targetRepositoryUrl);
        if (username != null) {
            targetRepository.setAuthentication(new Authentication(username, password));
        }

        try {
            Artifact resolvedRootArtifact = repoSystem.resolveArtifact(repoSession, artifactRequest).getArtifact();
            Dependency resolvedRootDependency = new Dependency(resolvedRootArtifact, null);

            DeployRequest deployRequest = new DeployRequest();
            deployRequest.setRepository(targetRepository);

//            InstallRequest installRequest = new InstallRequest();

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRepositories(projectRepos);
            collectRequest.setRoot(resolvedRootDependency);

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

            for (ArtifactResult resolvedDependency : repoSystem.resolveDependencies(repoSession, dependencyRequest).getArtifactResults()) {
                System.out.println("adding resolved dependency " + resolvedDependency.getArtifact());

                Artifact resolvedDependencyArtifact = resolvedDependency.getArtifact();

                if (!deployRequest.getArtifacts().contains(resolvedDependencyArtifact)) {
                    deployRequest.addArtifact(resolvedDependencyArtifact);
                }
//                installRequest.addArtifact(resolvedDependencyArtifact);

                ArtifactRequest resolvedDependencyPomRequest = new ArtifactRequest(new SubArtifact(resolvedDependencyArtifact, null, "pom"), projectRepos, null);
                ArtifactResult resolvedDependencyPomResult = repoSystem.resolveArtifact(repoSession, resolvedDependencyPomRequest);
                Artifact resolvedDependencyPomArtifact = resolvedDependencyPomResult.getArtifact();

                deployRequest.addArtifact(resolvedDependencyPomArtifact);
//                installRequest.addArtifact(resolvedDependencyPomArtifact);

                ArtifactResult parentResult = resolveParent(resolvedDependencyPomArtifact);
                while (parentResult != null) {
                    Artifact parentArtifact = parentResult.getArtifact();
                    if (!deployRequest.getArtifacts().contains(parentArtifact)) {
                        deployRequest.addArtifact(parentArtifact);
                    }
//                    if (!installRequest.getArtifacts().contains(parentArtifact)) {
//                        installRequest.addArtifact(parentArtifact);
//                    }
                    parentResult = resolveParent(parentArtifact);
                }
            }

//            LocalRepositoryManager localRepoManager = repoSystem.newLocalRepositoryManager(new LocalRepository(basedir));
//            DefaultRepositorySystemSession localSession = new DefaultRepositorySystemSession(repoSession);
//            localSession.setLocalRepositoryManager(localRepoManager);
//            repoSystem.install(localSession, installRequest);

            repoSystem.deploy(repoSession, deployRequest);
        }
        catch (ArtifactResolutionException e) {
            e.printStackTrace();
        }
//        catch (InstallationException e) {
//            e.printStackTrace();
//        }
        catch (ModelBuildingException e) {
            e.printStackTrace();
        }
        catch (DependencyResolutionException e) {
            e.printStackTrace();
        }
        catch (DeploymentException e) {
            e.printStackTrace();
        }
    }

    private ArtifactResult resolveParent(Artifact pomArtifact) throws ArtifactResolutionException, ModelBuildingException {
        ModelBuildingRequest buildingRequest = new DefaultModelBuildingRequest();
        buildingRequest.setModelResolver(new AvailableModelResolver(repoSession, null, null, artifactResolver, remoteRepositoryManager, projectRepos));
        buildingRequest.setPomFile(pomArtifact.getFile());
        buildingRequest.setTwoPhaseBuilding(false);
        buildingRequest.setSystemProperties(System.getProperties());
        buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        ModelBuildingResult build = modelBuilder.build(buildingRequest);
        Parent dependencyParent = build.getRawModel().getParent();
        if (dependencyParent != null) {
            ArtifactRequest parentArtifactRequest = new ArtifactRequest();
            parentArtifactRequest.setArtifact(new DefaultArtifact(dependencyParent.getGroupId(), dependencyParent.getArtifactId(), "pom", dependencyParent.getVersion()));
            parentArtifactRequest.setRepositories(projectRepos);
            return repoSystem.resolveArtifact(repoSession, parentArtifactRequest);
        } else {
            return null;
        }
    }

    static class AvailableModelResolver implements ModelResolver {

        private final RepositorySystemSession session;

        private final RequestTrace trace;

        private final String context;

        private List<RemoteRepository> repositories;

        private final ArtifactResolver resolver;

        private final RemoteRepositoryManager remoteRepositoryManager;

        private final Set<String> repositoryIds;

        public AvailableModelResolver(RepositorySystemSession session, RequestTrace trace, String context, ArtifactResolver resolver,
                RemoteRepositoryManager remoteRepositoryManager, List<RemoteRepository> repositories) {
            this.session = session;
            this.trace = trace;
            this.context = context;
            this.resolver = resolver;
            this.remoteRepositoryManager = remoteRepositoryManager;
            this.repositories = repositories;
            this.repositoryIds = new HashSet<String>();
        }

        private AvailableModelResolver(AvailableModelResolver original) {
            this.session = original.session;
            this.trace = original.trace;
            this.context = original.context;
            this.resolver = original.resolver;
            this.remoteRepositoryManager = original.remoteRepositoryManager;
            this.repositories = original.repositories;
            this.repositoryIds = new HashSet<String>(original.repositoryIds);
        }

        public void addRepository(Repository repository) throws InvalidRepositoryException {
            if (!repositoryIds.add(repository.getId())) {
                return;
            }

            List<RemoteRepository> newRepositories = Collections.singletonList(ArtifactDescriptorUtils.toRemoteRepository(repository));

            this.repositories = remoteRepositoryManager.aggregateRepositories(session, repositories, newRepositories, true);
        }

        public ModelResolver newCopy() {
            return new AvailableModelResolver(this);
        }

        public ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
            Artifact pomArtifact = new DefaultArtifact(groupId, artifactId, "", "pom", version);

            try {
                ArtifactRequest request = new ArtifactRequest(pomArtifact, repositories, context);
                request.setTrace(trace);
                pomArtifact = resolver.resolveArtifact(session, request).getArtifact();
            }
            catch (ArtifactResolutionException e) {
                throw new UnresolvableModelException(e.getMessage(), groupId, artifactId, version, e);
            }

            File pomFile = pomArtifact.getFile();

            return new FileModelSource(pomFile);
        }

    }

}
