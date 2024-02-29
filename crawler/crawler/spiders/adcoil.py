import datetime
from ..items import AdItem
import re
from scrapy.linkextractors import LinkExtractor
from scrapy.spiders import CrawlSpider, Rule

class AdcoilSpider(CrawlSpider):
    name = 'adcoil'
    allowed_domains = ['www.ad.co.il']
    start_urls = ['https://www.ad.co.il/']
    rules = (
        Rule(LinkExtractor(), callback='parse_item',follow=True),
    )

    def parse_item(self, response):
        CITY_SELECTOR = 'a.ps-2::attr(href)'
        POSTED_TIME_SELECTOR = 'div.px-3::text'
        city = self.city_from(response.css(CITY_SELECTOR).extract_first())
        if self.is_ad(response.url) and city:
            ad = AdItem()
            ad['ad_id'] = self.ad_id_from(response.url)
            ad['city'] = city
            ad['posted_date'] = self.posted_time_from(response.css(POSTED_TIME_SELECTOR).extract_first())
            ad['scraped_time'] = str(datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
            yield ad
            
    def is_ad(self, url):
        match = re.search("/ad/", url)
        return match != None
    
    def ad_id_from(self, url):
        return url.split("/ad/")[-1]
    
    
    def city_from(self, city_link):
        if city_link == None:
            return None
        return city_link.split("/")[-1]
    
    def posted_time_from(self, posted_time_string):
        if posted_time_string == None:
            return None
        # expecting posted_time_string = '>תאריך יצירה: 25/05/2022</div>'
        # self.logger.info(1,f'posted_time_string: {posted_time_string}')
        date_only = posted_time_string[-10:]
        # self.logger.info(1,f'date only: {date_only}')
        return str(datetime.datetime.strptime(date_only.replace("/", "-"), "%d-%m-%Y").date())
