/******************************************************************************
 * Copyright © 2020-2021    The BENED Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * BENED software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

/**
 * @depends {nrs.js}
 */
var NRS = (function(NRS) {
	
	NRS.pages.generators = function() {
        NRS.renderGenerators(false);
	};
	
	NRS.renderGenerators = function(isRefresh) {
		if (NRS.currentPage == "generators") {
			timer = setInterval(function() {
				if (NRS.currentPage != "generators") {
					clearInterval(timer);
				} else {
				NRS.sendRequest("getBNDActivGenerators", {}, function(response) {
					var rows = "";
					const objectArray = Object.entries(response);
					objectArray.forEach(([key, value]) => {
						rows += "<tr>";
						const itemArray = Object.entries(value);
						itemArray.forEach(([key, value]) => {
							rows += "<td>" + value;
							rows += "</td>";
						});
						rows += "</tr>";
					});
					$('#generators_table tbody').html(rows);
				});
			}
			}, 1000);
		}
	};
	
	return NRS;
	
}(NRS || {}, jQuery));
