/* *********************************************************************** *
 * project: org.matsim.*
 * UtilitiesBoxPlot.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.anhorni.surprice.analysis;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;

public class SupriceBoxPlot {
	
	private JFreeChart chart_;
	private String chartTitle;
	private DefaultBoxAndWhiskerCategoryDataset dataset;
	private String xAxisName = "";
	private String yAxisName = "";
	private double yrangeLower = 0.0;
	private double yrangeUpper = 0.0;

	public SupriceBoxPlot(final String chartTitle, String xAxisName, String yAxisName, double yrangeLower, double yrangeUpper) {
		dataset = new DefaultBoxAndWhiskerCategoryDataset();
		this.chartTitle = chartTitle;
		this.xAxisName = xAxisName;
		this.yAxisName = yAxisName;
		this.yrangeLower = yrangeLower;
		this.yrangeUpper = yrangeUpper;
	}
		
	public void addValuesPerCategory(ArrayList<Double> values, String category, String name) {
		dataset.add(values, name, category);
	}

	public JFreeChart createChart() {	
		String title = chartTitle;
		
		final CategoryAxis xAxis = new CategoryAxis(this.xAxisName);
		xAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 10));
		xAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
		
		final NumberAxis yAxis = new NumberAxis(this.yAxisName);
		yAxis.setAutoRangeIncludesZero(true);
		
		if (Math.abs(yrangeLower - yrangeUpper) > Double.MIN_VALUE) {
			yAxis.setRange(yrangeLower, yrangeUpper);
		}

		final BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer();
		renderer.setFillBox(false);
		renderer.setSeriesPaint(0, Color.blue);		
		CategoryPlot plot = new CategoryPlot(dataset, xAxis, yAxis, renderer);

		this.chart_ = new JFreeChart(title, new Font("SansSerif", Font.BOLD, 14), plot, false);
		return this.chart_;
	}
	
	public void saveAsPng(final String filename, final int width, final int height) {
		try {
			ChartUtilities.saveChartAsPNG(new File(filename), createChart(), width, height, null, true, 9);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}