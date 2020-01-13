package k8s_test

import (
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"path/filepath"
)

var _ = Describe("Deployment", func() {
	var deploymentPath, valuesPath string

	BeforeEach(func() {
		deploymentPath = pathToTemplate("deployment.yml")
		valuesPath = pathToTemplate(filepath.Join("values", "values.yml"))
	})

	It("Renders a deployment for the UAA", func() {
		ctx := NewRenderingContext(deploymentPath, valuesPath)

		Expect(ctx).To(
			ProduceYAML(
				RepresentingDeployment().WithPodMatching(func(pod *PodMatcher) {
					pod.WithContainerMatching(func(container *ContainerMatcher) {
						container.WithName("uaa")
					})
				}),
			),
		)
	})

	It("Renders common labels for the deployment", func() {
		ctx := NewRenderingContext(deploymentPath, valuesPath).WithData(map[string]string{
			"version": "1.0.0",
		})

		Expect(ctx).To(
			ProduceYAML(RepresentingDeployment().WithLabels(map[string]string{
				"app.kubernetes.io/name":       "uaa",
				"app.kubernetes.io/instance":   "uaa-standalone",
				"app.kubernetes.io/version":    "1.0.0",
				"app.kubernetes.io/component":  "authorization server",
				"app.kubernetes.io/part-of":    "uaa",
				"app.kubernetes.io/managed-by": "kapp",
			})),
		)
	})
})
